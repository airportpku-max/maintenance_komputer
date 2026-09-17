import React, { useState, useEffect, useMemo } from 'react';
import { 
  Wrench, 
  Calendar, 
  Plus, 
  Search, 
  Trash2, 
  Eye, 
  X, 
  Info, 
  Check, 
  CheckSquare, 
  Camera, 
  Save, 
  FileText, 
  ChevronLeft, 
  ChevronRight,
  Upload,
  User,
  MapPin,
  Laptop,
  Monitor
} from 'lucide-react';
import { Device, MaintenanceLog, ScreenRoute } from '../types';
import { useMaintenance } from '../context/MaintenanceContext';
import { SignatureCanvas } from '../components/SignatureCanvas';
import { uploadFileToR2 } from '../services/r2Storage';

interface InputMaintenanceScreenProps {
  onNavigate: (route: ScreenRoute) => void;
  preselectedDeviceId?: number | null;
}

// 8 Official Inspection Items (Matching Screenshot)
const INSPECTION_ITEMS = [
  { key: 'healthReport', label: 'Health Report', desc: 'Pemeriksaan HDD/SSD & S.M.A.R.T' },
  { key: 'diskCleanup', label: 'Disk Clean Up', desc: 'Pembersihan file cache & temporary' },
  { key: 'hardwareCleanup', label: 'Hardware Clean Up', desc: 'Pembersihan debu & sirkulasi fan' },
  { key: 'checkingDriveError', label: 'Checking Drive Error', desc: 'Pengecekan bad sector & drive' },
  { key: 'scanningVirus', label: 'Scanning Virus', desc: 'Pemindaian virus & malware' },
  { key: 'checkingNetwork', label: 'Checking Network', desc: 'Pengecekan koneksi intranet & gateway' },
  { key: 'updatingAntivirus', label: 'Updating Antivirus Databases', desc: 'Pembaruan definisi antivirus' },
  { key: 'updatingAplikasi', label: 'Updating Aplikasi', desc: 'Pembaruan aplikasi & patch sistem' },
] as const;

type ItemKey = typeof INSPECTION_ITEMS[number]['key'];

interface UserDeviceFormState {
  deviceId: number;
  userName: string;
  deviceType: string;
  brand: string;
  sn: string;
  checks: Record<ItemKey, boolean>;
  photosBefore: Record<ItemKey, string | null>;
  photosAfter: Record<ItemKey, string | null>;
  notes: string;
  userSignature: string;
  isExpanded: boolean;
}

export const InputMaintenanceScreen: React.FC<InputMaintenanceScreenProps> = ({ 
  onNavigate,
  preselectedDeviceId 
}) => {
  const { devices, maintenanceLogs, technicians, addMaintenanceLog, deleteMaintenanceLog } = useMaintenance();

  // Modals state
  const [isInputModalOpen, setIsInputModalOpen] = useState<boolean>(false);
  const [viewLogDetail, setViewLogDetail] = useState<MaintenanceLog | null>(null);

  // Filter & Search state for Riwayat Table
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [filterLocation, setFilterLocation] = useState<string>('Semua');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const itemsPerPage = 8;

  // Unique locations from devices
  const allLocations = useMemo(() => {
    const locSet = new Set<string>();
    devices.forEach(d => {
      if (d.description && d.description.trim()) {
        locSet.add(d.description.trim());
      }
    });
    return Array.from(locSet);
  }, [devices]);

  // Modal Form State: Date & Selected Location
  const [modalDate, setModalDate] = useState<string>(() => {
    const today = new Date();
    return today.toISOString().split('T')[0];
  });

  const [selectedLocation, setSelectedLocation] = useState<string>(() => {
    if (preselectedDeviceId) {
      const dev = devices.find(d => d.id === preselectedDeviceId);
      if (dev && dev.description) return dev.description;
    }
    // Default to Avsec or first available location
    const avsec = allLocations.find(loc => loc.toLowerCase().includes('avsec'));
    return avsec || (allLocations.length > 0 ? allLocations[0] : 'Gedung Avsec (Admin Pas)/OOSC');
  });

  // Automatically fetch devices at the selected location
  const devicesAtLocation = useMemo(() => {
    return devices.filter(d => d.description?.trim() === selectedLocation.trim());
  }, [devices, selectedLocation]);

  // Form states mapped by device ID
  const [userForms, setUserForms] = useState<Record<number, UserDeviceFormState>>({});

  // Initialize or update user forms whenever devicesAtLocation changes
  useEffect(() => {
    setUserForms(prev => {
      const next: Record<number, UserDeviceFormState> = {};
      devicesAtLocation.forEach(d => {
        if (prev[d.id]) {
          next[d.id] = prev[d.id];
        } else {
          next[d.id] = {
            deviceId: d.id,
            userName: d.name,
            deviceType: d.type,
            brand: d.brand,
            sn: d.sn || d.serialNumber || '-',
            checks: {
              healthReport: false,
              diskCleanup: false,
              hardwareCleanup: false,
              checkingDriveError: false,
              scanningVirus: false,
              checkingNetwork: false,
              updatingAntivirus: false,
              updatingAplikasi: false,
            },
            photosBefore: {
              healthReport: null,
              diskCleanup: null,
              hardwareCleanup: null,
              checkingDriveError: null,
              scanningVirus: null,
              checkingNetwork: null,
              updatingAntivirus: null,
              updatingAplikasi: null,
            },
            photosAfter: {
              healthReport: null,
              diskCleanup: null,
              hardwareCleanup: null,
              checkingDriveError: null,
              scanningVirus: null,
              checkingNetwork: null,
              updatingAntivirus: null,
              updatingAplikasi: null,
            },
            notes: 'baik',
            userSignature: '',
            isExpanded: false
          };
        }
      });
      return next;
    });
  }, [devicesAtLocation]);

  // Open modal if preselectedDeviceId is provided
  useEffect(() => {
    if (preselectedDeviceId) {
      const dev = devices.find(d => d.id === preselectedDeviceId);
      if (dev) {
        if (dev.description) {
          setSelectedLocation(dev.description);
        }
        setIsInputModalOpen(true);
      }
    }
  }, [preselectedDeviceId, devices]);

  // Toggle checklist item
  const handleToggleCheck = (deviceId: number, itemKey: ItemKey) => {
    setUserForms(prev => {
      const curr = prev[deviceId];
      if (!curr) return prev;
      return {
        ...prev,
        [deviceId]: {
          ...curr,
          checks: {
            ...curr.checks,
            [itemKey]: !curr.checks[itemKey]
          }
        }
      };
    });
  };

  // Toggle all items for a device
  const handleToggleAllForDevice = (deviceId: number, state: boolean) => {
    setUserForms(prev => {
      const curr = prev[deviceId];
      if (!curr) return prev;
      const allChecks: Record<ItemKey, boolean> = {
        healthReport: state,
        diskCleanup: state,
        hardwareCleanup: state,
        checkingDriveError: state,
        scanningVirus: state,
        checkingNetwork: state,
        updatingAntivirus: state,
        updatingAplikasi: state,
      };
      return {
        ...prev,
        [deviceId]: {
          ...curr,
          checks: allChecks
        }
      };
    });
  };

  // Handle photo file selection with Cloudflare R2 uploader
  const handlePhotoUpload = async (deviceId: number, itemKey: ItemKey, isBefore: boolean, file: File) => {
    // 1. Immediately show local preview
    const reader = new FileReader();
    reader.onloadend = () => {
      const result = reader.result as string;
      setUserForms(prev => {
        const curr = prev[deviceId];
        if (!curr) return prev;
        return {
          ...prev,
          [deviceId]: {
            ...curr,
            [isBefore ? 'photosBefore' : 'photosAfter']: {
              ...(isBefore ? curr.photosBefore : curr.photosAfter),
              [itemKey]: result
            }
          }
        };
      });
    };
    reader.readAsDataURL(file);

    // 2. Upload to Cloudflare R2 worker in background
    try {
      const r2Res = await uploadFileToR2(file);
      if (r2Res.success && r2Res.url) {
        setUserForms(prev => {
          const curr = prev[deviceId];
          if (!curr) return prev;
          return {
            ...prev,
            [deviceId]: {
              ...curr,
              [isBefore ? 'photosBefore' : 'photosAfter']: {
                ...(isBefore ? curr.photosBefore : curr.photosAfter),
                [itemKey]: r2Res.url
              }
            }
          };
        });
      }
    } catch (err) {
      console.warn('R2 upload failed, keeping base64 preview:', err);
    }
  };

  // Save Modal Form
  const handleSaveModal = () => {
    const targetTimestamp = new Date(modalDate).getTime();
    let savedCount = 0;

    devicesAtLocation.forEach(device => {
      const form = userForms[device.id];
      if (!form) return;

      // Check if user did any work (at least one checkbox, a photo, or custom note)
      const hasAnyCheck = Object.values(form.checks).some(val => val === true);
      const hasAnyPhoto = Object.values(form.photosBefore).some(p => p !== null) || Object.values(form.photosAfter).some(p => p !== null);
      const hasCustomNote = form.notes && form.notes.trim() !== '' && form.notes !== 'baik';
      const hasSignature = Boolean(form.userSignature);

      // User yang sama sekali tidak dikerjakan tidak akan masuk riwayat
      if (!hasAnyCheck && !hasAnyPhoto && !hasCustomNote && !hasSignature) {
        return;
      }

      // Add log
      addMaintenanceLog({
        deviceId: device.id,
        deviceName: `${device.name} (${device.brand})`,
        technicianName: technicians.length > 0 ? technicians[0].name : 'Arifatul Azhar',
        actionTaken: `Perawatan rutin perangkat di ${selectedLocation}`,
        healthReport: form.checks.healthReport,
        diskCleanup: form.checks.diskCleanup,
        hardwareCleanup: form.checks.hardwareCleanup,
        checkingDriveError: form.checks.checkingDriveError,
        scanningVirus: form.checks.scanningVirus,
        checkingNetwork: form.checks.checkingNetwork,
        updatingAntivirus: form.checks.updatingAntivirus,
        updatingAplikasi: form.checks.updatingAplikasi,
        windowsLicense: 'Windows 11 Pro OEM',
        officeLicense: 'Office 2021 LTSC',
        notes: form.notes || 'baik',
        signatureData: form.userSignature || null,
        techSignatureData: null,
        healthReportBeforePhoto: form.photosBefore.healthReport || form.photosBefore.hardwareCleanup,
        healthReportAfterPhoto: form.photosAfter.healthReport || form.photosAfter.hardwareCleanup,
        hardwareCleanupBeforePhoto: form.photosBefore.hardwareCleanup,
        hardwareCleanupAfterPhoto: form.photosAfter.hardwareCleanup
      });

      savedCount++;
    });

    if (savedCount > 0) {
      alert(`Berhasil menyimpan laporan pemeliharaan untuk ${savedCount} perangkat di ${selectedLocation}!`);
      setIsInputModalOpen(false);
    } else {
      alert('Tidak ada perangkat yang ditandai atau dikerjakan. Silakan centang setidaknya satu item pekerjaan.');
    }
  };

  // Filtered maintenance logs for Riwayat table
  const filteredLogs = useMemo(() => {
    return maintenanceLogs.filter(log => {
      const dev = devices.find(d => d.id === log.deviceId);
      const loc = dev?.description || '';
      
      const matchesSearch = 
        log.deviceName.toLowerCase().includes(searchTerm.toLowerCase()) ||
        loc.toLowerCase().includes(searchTerm.toLowerCase()) ||
        (dev?.sn && dev.sn.toLowerCase().includes(searchTerm.toLowerCase())) ||
        (log.notes && log.notes.toLowerCase().includes(searchTerm.toLowerCase()));

      const matchesLocation = filterLocation === 'Semua' || loc === filterLocation;

      return matchesSearch && matchesLocation;
    });
  }, [maintenanceLogs, devices, searchTerm, filterLocation]);

  // Pagination calculations
  const totalPages = Math.max(1, Math.ceil(filteredLogs.length / itemsPerPage));
  const currentLogs = useMemo(() => {
    const start = (currentPage - 1) * itemsPerPage;
    return filteredLogs.slice(start, start + itemsPerPage);
  }, [filteredLogs, currentPage]);

  const handleDeleteLog = (id: number) => {
    if (confirm('Apakah Anda yakin ingin menghapus data riwayat maintenance ini?')) {
      deleteMaintenanceLog(id);
    }
  };

  return (
    <div className="space-y-6 pb-20">
      {/* Top Header Information */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
        <div>
          <h1 className="text-xl font-bold text-slate-800 tracking-tight flex items-center gap-2.5">
            <span className="p-2 rounded-xl bg-emerald-50 text-[#0F7D3A] border border-emerald-100">
              <Wrench className="w-5 h-5" />
            </span>
            Input & Riwayat Maintenance
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Pencatatan pemeliharaan preventif per lokasi dan riwayat pemeliharaan perangkat bandara
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => setIsInputModalOpen(true)}
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl text-sm font-bold shadow-sm shadow-emerald-700/20 transition cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            Input Maintenance Baru
          </button>
        </div>
      </div>

      {/* Main Riwayat Maintenance Table Container */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {/* Card Header & Search Controls */}
        <div className="p-5 border-b border-slate-200 space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <h2 className="text-base font-bold text-slate-900">Riwayat Maintenance</h2>
              <p className="text-xs text-slate-500">
                Semua catatan riwayat pemeliharaan komputer dan laptop berkala
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-2.5">
              {/* Search Bar */}
              <div className="relative min-w-[220px]">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  placeholder="Cari user, serial number, atau lokasi..."
                  value={searchTerm}
                  onChange={e => {
                    setSearchTerm(e.target.value);
                    setCurrentPage(1);
                  }}
                  className="w-full pl-9 pr-3.5 py-2 text-xs border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#0F7D3A]"
                />
              </div>

              {/* Location Filter */}
              <select
                value={filterLocation}
                onChange={e => {
                  setFilterLocation(e.target.value);
                  setCurrentPage(1);
                }}
                className="px-3 py-2 text-xs font-semibold bg-slate-50 border border-slate-200 rounded-xl text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#0F7D3A] cursor-pointer"
              >
                <option value="Semua">Semua Lokasi</option>
                {allLocations.map(loc => (
                  <option key={loc} value={loc}>{loc}</option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {/* Table */}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-slate-600">
            <thead className="bg-slate-50 border-b border-slate-200 text-slate-700 uppercase font-bold text-[11px] tracking-wider">
              <tr>
                <th className="py-3 px-4">TANGGAL</th>
                <th className="py-3 px-4">LOKASI</th>
                <th className="py-3 px-4">USER / PERANGKAT</th>
                <th className="py-3 px-4 text-center">CHECKLIST DIKERJAKAN</th>
                <th className="py-3 px-4">KETERANGAN</th>
                <th className="py-3 px-4 text-center w-28">AKSI</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {currentLogs.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400">
                    <Wrench className="w-8 h-8 mx-auto mb-2 text-slate-300 opacity-50" />
                    Belum ada riwayat maintenance yang sesuai kriteria pencarian.
                  </td>
                </tr>
              ) : (
                currentLogs.map(log => {
                  const dev = devices.find(d => d.id === log.deviceId);
                  const logDate = new Date(log.timestamp);
                  const formattedDate = logDate.toLocaleDateString('id-ID', {
                    day: 'numeric',
                    month: 'short',
                    year: 'numeric'
                  });

                  // Count completed checklist items
                  const completedCount = [
                    log.healthReport,
                    log.diskCleanup,
                    log.hardwareCleanup,
                    log.checkingDriveError,
                    log.scanningVirus,
                    log.checkingNetwork,
                    log.updatingAntivirus,
                    log.updatingAplikasi
                  ].filter(Boolean).length;

                  return (
                    <tr key={log.id} className="hover:bg-slate-50/70 transition">
                      <td className="py-3.5 px-4 font-semibold text-slate-900 whitespace-nowrap">
                        {formattedDate}
                      </td>
                      <td className="py-3.5 px-4 font-medium text-slate-700">
                        {dev?.description || '-'}
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="font-bold text-slate-900">{log.deviceName}</div>
                        <div className="text-[11px] text-slate-400 font-mono">
                          SN: {dev?.sn || dev?.serialNumber || '-'}
                        </div>
                      </td>
                      <td className="py-3.5 px-4 text-center">
                        <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold ${
                          completedCount === 8
                            ? 'bg-emerald-50 text-[#0F7D3A] border border-emerald-200'
                            : 'bg-slate-100 text-slate-700 border border-slate-200'
                        }`}>
                          <Check className="w-3 h-3" />
                          {completedCount} / 8 Item
                        </span>
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="text-slate-600 italic">
                          {log.notes || 'baik'}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-center whitespace-nowrap">
                        <div className="flex items-center justify-center gap-1.5">
                          {/* Eye Detail Button */}
                          <button
                            onClick={() => setViewLogDetail(log)}
                            title="Lihat Detail"
                            className="p-1.5 bg-emerald-50 hover:bg-emerald-100 text-[#0F7D3A] border border-emerald-200/80 rounded-lg transition shadow-2xs cursor-pointer"
                          >
                            <Eye className="w-4 h-4" />
                          </button>
                          {/* Trash Delete Button */}
                          <button
                            onClick={() => handleDeleteLog(log.id)}
                            title="Hapus Riwayat"
                            className="p-1.5 bg-rose-50 hover:bg-rose-100 text-rose-600 border border-rose-200/80 rounded-lg transition shadow-2xs cursor-pointer"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Footer (Matching Screenshot PREV / NEXT style) */}
        <div className="p-4 border-t border-slate-200 bg-slate-50/50 flex items-center justify-between text-xs text-slate-600">
          <div>
            Halaman <strong className="text-slate-900">{currentPage}</strong> dari{' '}
            <strong className="text-slate-900">{totalPages}</strong> ({filteredLogs.length} total data)
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
              disabled={currentPage === 1}
              className="px-3.5 py-1.5 bg-white border border-slate-200 hover:bg-slate-100 disabled:opacity-40 disabled:hover:bg-white rounded-lg font-bold text-slate-700 shadow-xs transition cursor-pointer"
            >
              PREV
            </button>
            <button
              onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
              disabled={currentPage >= totalPages}
              className="px-3.5 py-1.5 bg-white border border-slate-200 hover:bg-slate-100 disabled:opacity-40 disabled:hover:bg-white rounded-lg font-bold text-slate-700 shadow-xs transition cursor-pointer"
            >
              NEXT
            </button>
          </div>
        </div>
      </div>

      {/* ==================================================================================================== */}
      {/* MODAL INPUT: "Maintenance per Lokasi" (HARMONIZED WITH APP THEME) */}
      {/* ==================================================================================================== */}
      {isInputModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-slate-900/60 backdrop-blur-xs overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl border border-slate-100 w-full max-w-4xl overflow-hidden flex flex-col my-auto max-h-[92vh]">
            {/* Modal Header: Emerald Airport Theme Banner */}
            <div className="bg-gradient-to-r from-[#0A5527] to-[#0F7D3A] text-white px-6 py-4 flex items-center justify-between select-none shadow-sm">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-white/15 flex items-center justify-center backdrop-blur-sm border border-white/20 shadow-inner">
                  <Wrench className="w-5 h-5 text-white" />
                </div>
                <div>
                  <span className="text-[10px] font-extrabold uppercase tracking-wider text-emerald-200 block">
                    INPUT
                  </span>
                  <h2 className="text-lg font-bold text-white leading-tight">
                    Maintenance per Lokasi
                  </h2>
                </div>
              </div>

              <button
                onClick={() => setIsInputModalOpen(false)}
                className="text-white/80 hover:text-white p-2 rounded-xl hover:bg-white/10 transition cursor-pointer"
                title="Tutup Modal"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Modal Body: Scrollable Content */}
            <div className="p-6 space-y-5 overflow-y-auto">
              {/* Row 1: TANGGAL & LOKASI Controls */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* TANGGAL */}
                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase mb-1.5 tracking-wider">
                    TANGGAL
                  </label>
                  <div className="relative">
                    <input
                      type="date"
                      value={modalDate}
                      onChange={e => setModalDate(e.target.value)}
                      className="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-slate-900 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-[#0F7D3A] focus:border-[#0F7D3A] shadow-xs"
                    />
                  </div>
                </div>

                {/* LOKASI */}
                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase mb-1.5 tracking-wider">
                    LOKASI
                  </label>
                  <select
                    value={selectedLocation}
                    onChange={e => setSelectedLocation(e.target.value)}
                    className="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-slate-900 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-[#0F7D3A] focus:border-[#0F7D3A] shadow-xs cursor-pointer"
                  >
                    {allLocations.map(loc => (
                      <option key={loc} value={loc}>
                        {loc}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Info Notice Box */}
              <div className="bg-emerald-50/70 border border-emerald-200/80 rounded-xl p-3.5 text-xs text-emerald-950 flex items-start gap-2.5">
                <Info className="w-4 h-4 text-[#0F7D3A] mt-0.5 shrink-0" />
                <p className="leading-relaxed">
                  Menampilkan <strong>{devicesAtLocation.length} user</strong> yang perlu dimaintenance. Laporan tetap dapat disimpan walau ada paraf yang belum terisi. User yang sama sekali tidak dikerjakan (tanpa checklist, foto, keterangan, maupun paraf) tidak akan masuk riwayat.
                </p>
              </div>

              {/* Devices & Users List At Selected Location */}
              {devicesAtLocation.length === 0 ? (
                <div className="text-center py-10 border-2 border-dashed border-slate-200 rounded-2xl">
                  <User className="w-10 h-10 mx-auto text-slate-300 mb-2" />
                  <p className="text-sm font-semibold text-slate-700">Tidak ada user/perangkat di lokasi ini</p>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Silakan pilih lokasi lain atau tambahkan perangkat baru di Data Master.
                  </p>
                </div>
              ) : (
                <div className="space-y-6">
                  {devicesAtLocation.map(device => {
                    const form = userForms[device.id];
                    if (!form) return null;

                    return (
                      <div 
                        key={device.id} 
                        className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs space-y-4"
                      >
                        {/* Device & User Info Header */}
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-3">
                          <div>
                            <h3 className="text-base font-bold text-slate-900">
                              {device.name}
                            </h3>
                            <p className="text-xs text-slate-500 font-medium">
                              {device.type} · {device.brand} · SN: {device.sn || device.serialNumber}
                            </p>
                          </div>

                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() => handleToggleAllForDevice(device.id, true)}
                              className="px-2.5 py-1 bg-emerald-50 text-[#0F7D3A] hover:bg-emerald-100 border border-emerald-200/60 rounded-lg text-xs font-semibold transition cursor-pointer"
                            >
                              Centang Semua
                            </button>
                            <button
                              type="button"
                              onClick={() => handleToggleAllForDevice(device.id, false)}
                              className="px-2.5 py-1 bg-slate-100 text-slate-600 hover:bg-slate-200 rounded-lg text-xs font-semibold transition cursor-pointer"
                            >
                              Reset
                            </button>
                          </div>
                        </div>

                        {/* Checklist Table */}
                        <div className="overflow-x-auto">
                          <table className="w-full text-xs text-slate-700">
                            <thead>
                              <tr className="border-b border-slate-200 text-slate-400 uppercase text-[10px] tracking-wider font-bold">
                                <th className="py-2 px-2 text-left w-2/5">ITEM PEKERJAAN</th>
                                <th className="py-2 px-2 text-left w-[30%]">FOTO SEBELUM</th>
                                <th className="py-2 px-2 text-left w-[30%]">FOTO SESUDAH</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                              {INSPECTION_ITEMS.map(item => {
                                const isChecked = form.checks[item.key];
                                const beforeImg = form.photosBefore[item.key];
                                const afterImg = form.photosAfter[item.key];

                                return (
                                  <tr key={item.key} className="hover:bg-slate-50/50">
                                    {/* Item Pekerjaan with Toggle Switch */}
                                    <td className="py-3 px-2">
                                      <div className="flex items-center gap-3">
                                        {/* Styled Toggle Button */}
                                        <button
                                          type="button"
                                          onClick={() => handleToggleCheck(device.id, item.key)}
                                          className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                                            isChecked ? 'bg-[#0F7D3A]' : 'bg-slate-200'
                                          }`}
                                        >
                                          <span
                                            className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                                              isChecked ? 'translate-x-4' : 'translate-x-0'
                                            }`}
                                          />
                                        </button>

                                        <span className="font-bold text-slate-800 text-xs select-none cursor-pointer" onClick={() => handleToggleCheck(device.id, item.key)}>
                                          {item.label}
                                        </span>
                                      </div>
                                    </td>

                                    {/* Foto Sebelum */}
                                    <td className="py-3 px-2">
                                      <div className="flex items-center gap-2">
                                        <label className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-50 hover:bg-emerald-50 hover:border-emerald-300 hover:text-[#0F7D3A] border border-slate-300 rounded-lg text-xs font-medium text-slate-700 cursor-pointer transition shadow-2xs">
                                          <Camera className="w-3.5 h-3.5 text-slate-400" />
                                          <span>Choose file</span>
                                          <input
                                            type="file"
                                            accept="image/*"
                                            onChange={e => {
                                              if (e.target.files?.[0]) {
                                                handlePhotoUpload(device.id, item.key, true, e.target.files[0]);
                                              }
                                            }}
                                            className="hidden"
                                          />
                                        </label>

                                        {beforeImg ? (
                                          <div className="relative group">
                                            <img
                                              src={beforeImg}
                                              alt="Foto Sebelum"
                                              className="w-7 h-7 object-cover rounded-md border border-slate-200"
                                            />
                                          </div>
                                        ) : (
                                          <span className="text-[11px] text-slate-400 italic">
                                            No file chosen
                                          </span>
                                        )}
                                      </div>
                                    </td>

                                    {/* Foto Sesudah */}
                                    <td className="py-3 px-2">
                                      <div className="flex items-center gap-2">
                                        <label className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-50 hover:bg-emerald-50 hover:border-emerald-300 hover:text-[#0F7D3A] border border-slate-300 rounded-lg text-xs font-medium text-slate-700 cursor-pointer transition shadow-2xs">
                                          <Camera className="w-3.5 h-3.5 text-slate-400" />
                                          <span>Choose file</span>
                                          <input
                                            type="file"
                                            accept="image/*"
                                            onChange={e => {
                                              if (e.target.files?.[0]) {
                                                handlePhotoUpload(device.id, item.key, false, e.target.files[0]);
                                              }
                                            }}
                                            className="hidden"
                                          />
                                        </label>

                                        {afterImg ? (
                                          <div className="relative group">
                                            <img
                                              src={afterImg}
                                              alt="Foto Sesudah"
                                              className="w-7 h-7 object-cover rounded-md border border-slate-200"
                                            />
                                          </div>
                                        ) : (
                                          <span className="text-[11px] text-slate-400 italic">
                                            No file chosen
                                          </span>
                                        )}
                                      </div>
                                    </td>
                                  </tr>
                                );
                              })}
                            </tbody>
                          </table>
                        </div>

                        {/* Collapsible Section for Keterangan & Digital Signature */}
                        <div className="pt-2 border-t border-slate-100">
                          <button
                            type="button"
                            onClick={() => {
                              setUserForms(prev => ({
                                ...prev,
                                [device.id]: {
                                  ...prev[device.id],
                                  isExpanded: !prev[device.id].isExpanded
                                }
                              }));
                            }}
                            className="text-xs text-[#0F7D3A] hover:text-[#0A5527] font-bold inline-flex items-center gap-1 cursor-pointer"
                          >
                            {form.isExpanded ? '▲ Sembunyikan Keterangan & Paraf' : '▼ Tambah Keterangan & Paraf User (Opsional)'}
                          </button>

                          {form.isExpanded && (
                            <div className="mt-3 grid grid-cols-1 md:grid-cols-2 gap-4 bg-slate-50 p-3.5 rounded-xl border border-slate-200">
                              <div>
                                <label className="block text-xs font-semibold text-slate-700 mb-1">
                                  Keterangan / Kondisi Unit
                                </label>
                                <input
                                  type="text"
                                  value={form.notes}
                                  onChange={e => {
                                    const val = e.target.value;
                                    setUserForms(prev => ({
                                      ...prev,
                                      [device.id]: {
                                        ...prev[device.id],
                                        notes: val
                                      }
                                    }));
                                  }}
                                  placeholder="Contoh: baik, unit stabil, dsb."
                                  className="w-full px-3 py-1.5 text-xs bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#0F7D3A]"
                                />
                              </div>

                              <div>
                                <SignatureCanvas
                                  label={`Paraf / Tanda Tangan User (${device.name})`}
                                  onSave={dataUrl => {
                                    setUserForms(prev => ({
                                      ...prev,
                                      [device.id]: {
                                        ...prev[device.id],
                                        userSignature: dataUrl
                                      }
                                    }));
                                  }}
                                  existingSignature={form.userSignature}
                                />
                              </div>
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="px-6 py-4 bg-slate-50 border-t border-slate-200 flex items-center justify-end gap-3 select-none">
              <button
                type="button"
                onClick={() => setIsInputModalOpen(false)}
                className="px-5 py-2.5 bg-white border border-slate-300 hover:bg-slate-100 text-slate-700 rounded-xl text-sm font-semibold transition cursor-pointer shadow-2xs"
              >
                Batal
              </button>
              <button
                type="button"
                onClick={handleSaveModal}
                className="inline-flex items-center gap-2 px-6 py-2.5 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl text-sm font-bold shadow-md shadow-emerald-700/20 transition cursor-pointer"
              >
                <Save className="w-4 h-4" />
                Simpan Laporan
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ==================================================================================================== */}
      {/* MODAL DETAIL: VIEW MAINTENANCE LOG DETAIL (EYE ACTION BUTTON) */}
      {/* ==================================================================================================== */}
      {viewLogDetail && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-slate-900/60 backdrop-blur-xs overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl border border-slate-100 w-full max-w-2xl overflow-hidden flex flex-col my-auto max-h-[90vh]">
            <div className="bg-gradient-to-r from-[#0A5527] to-[#0F7D3A] text-white px-6 py-4 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <Eye className="w-5 h-5 text-white" />
                <h3 className="font-bold text-base text-white">Detail Riwayat Pemeliharaan</h3>
              </div>
              <button
                onClick={() => setViewLogDetail(null)}
                className="text-white/80 hover:text-white p-1.5 rounded-lg hover:bg-white/10 transition cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-5 overflow-y-auto text-xs text-slate-700">
              <div className="grid grid-cols-2 gap-4 bg-slate-50 p-4 rounded-xl border border-slate-200">
                <div>
                  <span className="text-slate-400 block text-[11px]">Nama Perangkat / User:</span>
                  <strong className="text-slate-900 text-sm">{viewLogDetail.deviceName}</strong>
                </div>
                <div>
                  <span className="text-slate-400 block text-[11px]">Tanggal Pemeliharaan:</span>
                  <strong className="text-slate-900 text-sm">
                    {new Date(viewLogDetail.timestamp).toLocaleDateString('id-ID', {
                      day: 'numeric',
                      month: 'long',
                      year: 'numeric'
                    })}
                  </strong>
                </div>
                <div>
                  <span className="text-slate-400 block text-[11px]">Teknisi Pemeriksa:</span>
                  <strong className="text-slate-900">{viewLogDetail.technicianName}</strong>
                </div>
                <div>
                  <span className="text-slate-400 block text-[11px]">Keterangan:</span>
                  <strong className="text-slate-900">{viewLogDetail.notes || 'baik'}</strong>
                </div>
              </div>

              {/* Checklist Grid */}
              <div>
                <h4 className="font-bold text-slate-900 mb-2 uppercase tracking-wide text-[11px]">
                  Pemeriksaan 8 Pilar Checklist:
                </h4>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {[
                    { label: 'Health Report', val: viewLogDetail.healthReport },
                    { label: 'Disk Clean Up', val: viewLogDetail.diskCleanup },
                    { label: 'Hardware Clean Up', val: viewLogDetail.hardwareCleanup },
                    { label: 'Checking Drive Error', val: viewLogDetail.checkingDriveError },
                    { label: 'Scanning Virus', val: viewLogDetail.scanningVirus },
                    { label: 'Checking Network', val: viewLogDetail.checkingNetwork },
                    { label: 'Updating Antivirus', val: viewLogDetail.updatingAntivirus },
                    { label: 'Updating Aplikasi', val: viewLogDetail.updatingAplikasi },
                  ].map(c => (
                    <div
                      key={c.label}
                      className={`p-2.5 rounded-lg border flex items-center justify-between ${
                        c.val
                          ? 'bg-emerald-50/70 border-emerald-200 text-emerald-800'
                          : 'bg-slate-50 border-slate-200 text-slate-400'
                      }`}
                    >
                      <span className="font-semibold">{c.label}</span>
                      <span className="font-bold">{c.val ? '✓ Selesai' : '—'}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Signatures */}
              <div className="grid grid-cols-2 gap-4 border-t border-slate-200 pt-4">
                <div className="text-center">
                  <p className="text-slate-500 text-[11px] mb-1">Paraf Petugas / Teknisi</p>
                  <div className="h-16 flex items-center justify-center border border-dashed border-slate-300 rounded-lg bg-slate-50">
                    {viewLogDetail.techSignatureData ? (
                      <img src={viewLogDetail.techSignatureData} alt="Paraf Petugas" className="max-h-12 object-contain" />
                    ) : (
                      <span className="text-slate-400 italic text-[11px]">Tervalidasi Sistem</span>
                    )}
                  </div>
                </div>

                <div className="text-center">
                  <p className="text-slate-500 text-[11px] mb-1">Paraf User / Pemakai</p>
                  <div className="h-16 flex items-center justify-center border border-dashed border-slate-300 rounded-lg bg-slate-50">
                    {viewLogDetail.signatureData ? (
                      <img src={viewLogDetail.signatureData} alt="Paraf User" className="max-h-12 object-contain" />
                    ) : (
                      <span className="text-slate-400 italic text-[11px]">Belum bertanda tangan</span>
                    )}
                  </div>
                </div>
              </div>
            </div>

            <div className="p-4 bg-slate-50 border-t border-slate-200 flex justify-end">
              <button
                onClick={() => setViewLogDetail(null)}
                className="px-5 py-2 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl text-xs font-bold transition cursor-pointer shadow-sm"
              >
                Tutup
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
