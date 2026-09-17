import React, { useState } from 'react';
import { 
  Plus, 
  Search, 
  Monitor, 
  Laptop, 
  Edit, 
  Trash2, 
  Eye, 
  CheckCircle2, 
  AlertTriangle, 
  Printer, 
  X, 
  Save, 
  FileText,
  Camera,
  MapPin,
  Calendar,
  Hash,
  Wrench
} from 'lucide-react';
import { Device, ScreenRoute } from '../types';
import { useMaintenance } from '../context/MaintenanceContext';
import { BarcodeVisual } from '../components/BarcodeVisual';
import { uploadFileToR2 } from '../services/r2Storage';

interface DataMasterScreenProps {
  onNavigate: (route: ScreenRoute) => void;
  onSelectDeviceForMaintenance?: (deviceId: number) => void;
}

export const DataMasterScreen: React.FC<DataMasterScreenProps> = ({ 
  onNavigate,
  onSelectDeviceForMaintenance 
}) => {
  const { devices, addDevice, updateDevice, deleteDevice } = useMaintenance();

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedTab, setSelectedTab] = useState<'Semua' | 'AIO' | 'Laptop'>('Semua');

  // Modal states
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null);

  // Form states
  const [formData, setFormData] = useState<{
    type: 'AIO' | 'Laptop';
    name: string;
    brand: string;
    serialNumber: string;
    condition: 'Baik' | 'Trouble';
    description: string;
    sn: string;
    photoUri: string;
    baFileName: string;
  }>({
    type: 'AIO',
    name: '',
    brand: '',
    serialNumber: '',
    condition: 'Baik',
    description: '',
    sn: '',
    photoUri: '',
    baFileName: ''
  });

  const generateBarcode = (type: 'AIO' | 'Laptop') => {
    const prefix = type === 'AIO' ? 'BC-AIO' : 'BC-LAP';
    const rand = Math.floor(10000 + Math.random() * 90000);
    return `${prefix}-${rand}`;
  };

  const openAddModal = () => {
    setFormData({
      type: 'AIO',
      name: '',
      brand: '',
      serialNumber: generateBarcode('AIO'),
      condition: 'Baik',
      description: '',
      sn: '',
      photoUri: '',
      baFileName: ''
    });
    setShowAddModal(true);
  };

  const openEditModal = (device: Device) => {
    setSelectedDevice(device);
    setFormData({
      type: device.type,
      name: device.name,
      brand: device.brand,
      serialNumber: device.serialNumber,
      condition: device.condition,
      description: device.description,
      sn: device.sn,
      photoUri: device.photoUri || '',
      baFileName: device.baFileName || ''
    });
    setShowEditModal(true);
  };

  const handleTypeChange = (newType: 'AIO' | 'Laptop') => {
    setFormData(prev => ({
      ...prev,
      type: newType,
      serialNumber: generateBarcode(newType)
    }));
  };

  const handleSaveNew = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name.trim() || !formData.brand.trim()) {
      alert('Nama User dan Tipe/Merek perangkat wajib diisi!');
      return;
    }

    addDevice({
      type: formData.type,
      name: formData.name,
      brand: formData.brand,
      serialNumber: formData.serialNumber,
      condition: formData.condition,
      lastMaintenance: Date.now(),
      description: formData.description,
      sn: formData.sn,
      photoUri: formData.photoUri || null,
      baFileUri: null,
      baFileName: formData.baFileName || null
    });

    setShowAddModal(false);
  };

  const handleSaveEdit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedDevice) return;

    updateDevice({
      ...selectedDevice,
      type: formData.type,
      name: formData.name,
      brand: formData.brand,
      serialNumber: formData.serialNumber,
      condition: formData.condition,
      description: formData.description,
      sn: formData.sn,
      photoUri: formData.photoUri || null,
      baFileName: formData.baFileName || null
    });

    setShowEditModal(false);
    setSelectedDevice(null);
  };

  const handleDelete = (id: number, name: string) => {
    if (window.confirm(`Yakin ingin menghapus perangkat milik "${name}"?`)) {
      deleteDevice(id);
    }
  };

  // Filter devices
  const filteredDevices = devices.filter(device => {
    const matchesSearch = 
      device.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      device.brand.toLowerCase().includes(searchQuery.toLowerCase()) ||
      device.serialNumber.toLowerCase().includes(searchQuery.toLowerCase()) ||
      device.sn.toLowerCase().includes(searchQuery.toLowerCase()) ||
      device.description.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesTab = 
      selectedTab === 'Semua' || 
      device.type === selectedTab;

    return matchesSearch && matchesTab;
  });

  return (
    <div className="space-y-6">
      {/* Top Header Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <h1 className="text-xl font-bold text-slate-800 flex items-center gap-2">
            <Monitor className="w-5 h-5 text-[#0F7D3A]" />
            Data Master Perangkat Komputer
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Daftar lengkap AIO PC dan Laptop terdaftar di Bandara Sultan Syarif Kasim II
          </p>
        </div>

        <button
          onClick={openAddModal}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl font-bold text-xs sm:text-sm shadow-sm transition cursor-pointer self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          Tambah Perangkat
        </button>
      </div>

      {/* Search & Tabs */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        {/* Filter Tabs */}
        <div className="inline-flex p-1 bg-slate-200/70 rounded-xl">
          {(['Semua', 'AIO', 'Laptop'] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setSelectedTab(tab)}
              className={`px-4 py-1.5 rounded-lg text-xs font-semibold transition cursor-pointer ${
                selectedTab === tab
                  ? 'bg-white text-[#0A5527] shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {tab === 'Semua' ? `Semua (${devices.length})` : tab === 'AIO' ? `PC AIO (${devices.filter(d => d.type === 'AIO').length})` : `Laptop (${devices.filter(d => d.type === 'Laptop').length})`}
            </button>
          ))}
        </div>

        {/* Search Input */}
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Cari user, merek, barcode, lokasi, SN..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-white rounded-xl border border-slate-200 text-xs sm:text-sm focus:outline-none focus:ring-2 focus:ring-[#0F7D3A] transition"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>

      {/* Devices Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-5">
        {filteredDevices.length === 0 ? (
          <div className="col-span-full py-16 text-center bg-white rounded-2xl border border-dashed border-slate-300">
            <Monitor className="w-12 h-12 mx-auto text-slate-300 mb-3" />
            <p className="text-slate-600 font-semibold text-sm">Tidak ada perangkat yang sesuai</p>
            <p className="text-slate-400 text-xs mt-1">Coba ubah kata kunci pencarian atau tambah data baru.</p>
          </div>
        ) : (
          filteredDevices.map(device => (
            <div
              key={device.id}
              className="bg-white rounded-2xl border border-slate-200 hover:border-emerald-300 transition shadow-sm hover:shadow-md flex flex-col justify-between overflow-hidden group"
            >
              {/* Card Header & Photo */}
              <div>
                <div className="relative h-36 bg-slate-100 overflow-hidden border-b border-slate-100">
                  {device.photoUri ? (
                    <img 
                      src={device.photoUri} 
                      alt={device.name} 
                      className="w-full h-full object-cover group-hover:scale-105 transition duration-300"
                    />
                  ) : (
                    <div className="w-full h-full flex flex-col items-center justify-center text-slate-300 bg-slate-50">
                      {device.type === 'AIO' ? <Monitor className="w-10 h-10" /> : <Laptop className="w-10 h-10" />}
                      <span className="text-[11px] font-medium mt-1">Tidak ada foto</span>
                    </div>
                  )}

                  {/* Condition Badge */}
                  <span className={`absolute top-3 left-3 text-[11px] font-bold px-2.5 py-0.5 rounded-full backdrop-blur-sm shadow-sm ${
                    device.condition === 'Baik' 
                      ? 'bg-emerald-600/90 text-white' 
                      : 'bg-amber-500/90 text-white'
                  }`}>
                    {device.condition}
                  </span>

                  {/* Type Badge */}
                  <span className="absolute top-3 right-3 text-[10px] font-bold px-2 py-0.5 rounded-full bg-slate-900/80 text-white backdrop-blur-sm">
                    {device.type}
                  </span>
                </div>

                {/* Device Info */}
                <div className="p-4 space-y-2.5">
                  <div>
                    <h3 className="font-bold text-slate-900 text-base leading-snug line-clamp-1">
                      {device.name}
                    </h3>
                    <p className="text-xs font-semibold text-emerald-800 line-clamp-1">
                      {device.brand}
                    </p>
                  </div>

                  <div className="space-y-1 text-xs text-slate-600 pt-1 border-t border-slate-100">
                    <div className="flex items-center gap-1.5">
                      <Hash className="w-3.5 h-3.5 text-slate-400 flex-shrink-0" />
                      <span className="font-mono font-semibold text-slate-700">{device.serialNumber}</span>
                      {device.sn && <span className="text-slate-400">({device.sn})</span>}
                    </div>

                    <div className="flex items-center gap-1.5">
                      <MapPin className="w-3.5 h-3.5 text-slate-400 flex-shrink-0" />
                      <span className="line-clamp-1 text-slate-600">{device.description || 'Lokasi belum diset'}</span>
                    </div>

                    <div className="flex items-center gap-1.5">
                      <Calendar className="w-3.5 h-3.5 text-slate-400 flex-shrink-0" />
                      <span className="text-slate-500">
                        Maintenance: {new Date(device.lastMaintenance).toLocaleDateString('id-ID')}
                      </span>
                    </div>
                  </div>

                  {device.baFileName && (
                    <div className="inline-flex items-center gap-1 text-[11px] text-blue-700 bg-blue-50 px-2 py-0.5 rounded-md border border-blue-200/60 font-medium">
                      <FileText className="w-3 h-3" />
                      <span className="line-clamp-1">{device.baFileName}</span>
                    </div>
                  )}
                </div>
              </div>

              {/* Action Buttons */}
              <div className="p-3 bg-slate-50/80 border-t border-slate-100 flex items-center justify-between gap-1">
                <button
                  onClick={() => {
                    setSelectedDevice(device);
                    setShowDetailModal(true);
                  }}
                  className="p-1.5 text-slate-600 hover:text-emerald-700 hover:bg-emerald-50 rounded-lg transition cursor-pointer"
                  title="Lihat Detail & Barcode"
                >
                  <Eye className="w-4 h-4" />
                </button>

                <button
                  onClick={() => {
                    if (onSelectDeviceForMaintenance) {
                      onSelectDeviceForMaintenance(device.id);
                    }
                    onNavigate('batch_maintenance');
                  }}
                  className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-semibold text-emerald-800 bg-emerald-100/70 hover:bg-emerald-100 rounded-lg transition cursor-pointer"
                  title="Lakukan Maintenance Ceklis"
                >
                  <Wrench className="w-3.5 h-3.5" />
                  <span>Maintenance</span>
                </button>

                <div className="flex items-center gap-1">
                  <button
                    onClick={() => openEditModal(device)}
                    className="p-1.5 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition cursor-pointer"
                    title="Edit Data"
                  >
                    <Edit className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleDelete(device.id, device.name)}
                    className="p-1.5 text-slate-500 hover:text-red-600 hover:bg-red-50 rounded-lg transition cursor-pointer"
                    title="Hapus Perangkat"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Add Device Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl space-y-4 my-8 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-bold text-lg text-slate-800">Tambah Perangkat Baru</h3>
              <button onClick={() => setShowAddModal(false)} className="text-slate-400 hover:text-slate-600 cursor-pointer">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveNew} className="space-y-4 text-xs sm:text-sm">
              {/* Device Type */}
              <div>
                <label className="block font-semibold text-slate-700 mb-1.5">Jenis Perangkat</label>
                <div className="grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    onClick={() => handleTypeChange('AIO')}
                    className={`flex items-center justify-center gap-2 py-2.5 rounded-xl border font-bold transition cursor-pointer ${
                      formData.type === 'AIO'
                        ? 'border-[#0F7D3A] bg-emerald-50 text-[#0F7D3A]'
                        : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    <Monitor className="w-4 h-4" /> PC AIO
                  </button>
                  <button
                    type="button"
                    onClick={() => handleTypeChange('Laptop')}
                    className={`flex items-center justify-center gap-2 py-2.5 rounded-xl border font-bold transition cursor-pointer ${
                      formData.type === 'Laptop'
                        ? 'border-[#0F7D3A] bg-emerald-50 text-[#0F7D3A]'
                        : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    <Laptop className="w-4 h-4" /> Laptop
                  </button>
                </div>
              </div>

              {/* Barcode ID (Auto-Generated) */}
              <div>
                <label className="block font-semibold text-slate-700 mb-1">
                  ID Barcode (Otomatis dibuat)
                </label>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    value={formData.serialNumber}
                    readOnly
                    className="flex-1 px-3 py-2 bg-slate-100 border border-slate-200 rounded-xl font-mono font-bold text-slate-800"
                  />
                  <button
                    type="button"
                    onClick={() => setFormData(p => ({ ...p, serialNumber: generateBarcode(p.type) }))}
                    className="px-3 py-2 bg-slate-200 hover:bg-slate-300 text-slate-700 rounded-xl font-medium text-xs cursor-pointer"
                  >
                    Regenerate
                  </button>
                </div>
              </div>

              {/* Nama User */}
              <div>
                <label className="block font-semibold text-slate-700 mb-1">Nama User / Penanggung Jawab *</label>
                <input
                  type="text"
                  required
                  placeholder="Contoh: Budi Santoso (Staff Check-in)"
                  value={formData.name}
                  onChange={e => setFormData(p => ({ ...p, name: e.target.value }))}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                />
              </div>

              {/* Tipe & Merek Perangkat */}
              <div>
                <label className="block font-semibold text-slate-700 mb-1">Tipe & Merek Perangkat *</label>
                <input
                  type="text"
                  required
                  placeholder="Contoh: HP ProOne 440 G9 AIO / Lenovo ThinkPad L14"
                  value={formData.brand}
                  onChange={e => setFormData(p => ({ ...p, brand: e.target.value }))}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                />
              </div>

              {/* Lokasi & SN Hardware */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Lokasi / Unit Kerja</label>
                  <input
                    type="text"
                    placeholder="Ruang Check-in, Avsec, dsb."
                    value={formData.description}
                    onChange={e => setFormData(p => ({ ...p, description: e.target.value }))}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                  />
                </div>
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Serial Number (SN)</label>
                  <input
                    type="text"
                    placeholder="SN Fisik Perangkat"
                    value={formData.sn}
                    onChange={e => setFormData(p => ({ ...p, sn: e.target.value }))}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                  />
                </div>
              </div>

              {/* Kondisi */}
              <div>
                <label className="block font-semibold text-slate-700 mb-1">Kondisi Awal</label>
                <select
                  value={formData.condition}
                  onChange={e => setFormData(p => ({ ...p, condition: e.target.value as 'Baik' | 'Trouble' }))}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl bg-white focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                >
                  <option value="Baik">Baik (Normal)</option>
                  <option value="Trouble">Trouble (Bermasalah)</option>
                </select>
              </div>

              {/* Foto Fisik URL or Upload */}
              <div>
                <label className="block font-semibold text-slate-700 mb-1">Foto Fisik Perangkat (URL atau Unggah)</label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    placeholder="URL Foto atau pilih file..."
                    value={formData.photoUri}
                    onChange={e => setFormData(p => ({ ...p, photoUri: e.target.value }))}
                    className="flex-1 px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none text-xs"
                  />
                    <label className="px-3 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl font-medium text-xs cursor-pointer flex items-center gap-1">
                    <Camera className="w-3.5 h-3.5" />
                    File
                    <input
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={async (e) => {
                        const file = e.target.files?.[0];
                        if (file) {
                          const reader = new FileReader();
                          reader.onloadend = () => {
                            setFormData(p => ({ ...p, photoUri: reader.result as string }));
                          };
                          reader.readAsDataURL(file);

                          try {
                            const res = await uploadFileToR2(file);
                            if (res.success && res.url) {
                              setFormData(p => ({ ...p, photoUri: res.url }));
                            }
                          } catch (err) {
                            console.warn('R2 upload failed:', err);
                          }
                        }
                      }}
                    />
                  </label>
                </div>
              </div>

              {/* Nama Berkas BA */}
              <div>
                <label className="block font-semibold text-slate-700 mb-1">Nama File Berita Acara (BA)</label>
                <input
                  type="text"
                  placeholder="Contoh: BA-Serah-Terima-Unit-01.pdf"
                  value={formData.baFileName}
                  onChange={e => setFormData(p => ({ ...p, baFileName: e.target.value }))}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                />
              </div>

              {/* Buttons */}
              <div className="flex justify-end gap-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-700 hover:bg-slate-50 rounded-xl font-medium transition cursor-pointer"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl font-bold transition shadow-sm cursor-pointer"
                >
                  Simpan Perangkat
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Device Modal */}
      {showEditModal && selectedDevice && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl space-y-4 my-8 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-bold text-lg text-slate-800">Edit Data Perangkat</h3>
              <button onClick={() => setShowEditModal(false)} className="text-slate-400 hover:text-slate-600 cursor-pointer">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveEdit} className="space-y-4 text-xs sm:text-sm">
              <div>
                <label className="block font-semibold text-slate-700 mb-1">ID Barcode</label>
                <input
                  type="text"
                  value={formData.serialNumber}
                  readOnly
                  className="w-full px-3 py-2 bg-slate-100 border border-slate-200 rounded-xl font-mono font-bold text-slate-800"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Nama User / Penanggung Jawab *</label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={e => setFormData(p => ({ ...p, name: e.target.value }))}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Tipe & Merek Perangkat *</label>
                <input
                  type="text"
                  required
                  value={formData.brand}
                  onChange={e => setFormData(p => ({ ...p, brand: e.target.value }))}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Lokasi</label>
                  <input
                    type="text"
                    value={formData.description}
                    onChange={e => setFormData(p => ({ ...p, description: e.target.value }))}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                  />
                </div>
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Serial Number (SN)</label>
                  <input
                    type="text"
                    value={formData.sn}
                    onChange={e => setFormData(p => ({ ...p, sn: e.target.value }))}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Kondisi</label>
                <select
                  value={formData.condition}
                  onChange={e => setFormData(p => ({ ...p, condition: e.target.value as 'Baik' | 'Trouble' }))}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl bg-white focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                >
                  <option value="Baik">Baik (Normal)</option>
                  <option value="Trouble">Trouble (Bermasalah)</option>
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Foto Fisik (URL / Gambar)</label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    placeholder="URL Foto atau pilih file..."
                    value={formData.photoUri}
                    onChange={e => setFormData(p => ({ ...p, photoUri: e.target.value }))}
                    className="flex-1 px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none text-xs"
                  />
                  <label className="px-3 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl font-medium text-xs cursor-pointer flex items-center gap-1">
                    <Camera className="w-3.5 h-3.5" />
                    File
                    <input
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={async (e) => {
                        const file = e.target.files?.[0];
                        if (file) {
                          const reader = new FileReader();
                          reader.onloadend = () => {
                            setFormData(p => ({ ...p, photoUri: reader.result as string }));
                          };
                          reader.readAsDataURL(file);

                          try {
                            const res = await uploadFileToR2(file);
                            if (res.success && res.url) {
                              setFormData(p => ({ ...p, photoUri: res.url }));
                            }
                          } catch (err) {
                            console.warn('R2 upload failed:', err);
                          }
                        }
                      }}
                    />
                  </label>
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowEditModal(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-700 hover:bg-slate-50 rounded-xl font-medium transition cursor-pointer"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl font-bold transition shadow-sm cursor-pointer"
                >
                  Simpan Perubahan
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Device Detail & Sticker Modal */}
      {showDetailModal && selectedDevice && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl space-y-5 my-8 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <Monitor className="w-5 h-5 text-[#0F7D3A]" />
                <h3 className="font-bold text-base text-slate-800">Detail Perangkat & Barcode</h3>
              </div>
              <button onClick={() => setShowDetailModal(false)} className="text-slate-400 hover:text-slate-600 cursor-pointer">
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Sticker / Barcode Representation */}
            <div className="bg-slate-50 p-4 rounded-xl border border-slate-200 flex flex-col items-center text-center space-y-3">
              <div className="flex items-center gap-2 text-xs font-bold text-emerald-800">
                <span>BANDARA SULTAN SYARIF KASIM II</span>
              </div>
              <BarcodeVisual value={selectedDevice.serialNumber} type="barcode" />
              <BarcodeVisual value={selectedDevice.serialNumber} type="qr" />
              <p className="text-[11px] text-slate-500">
                Label sticker inventaris resmi aset komputer bandara.
              </p>
              <button
                onClick={() => window.print()}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 text-white rounded-lg text-xs font-semibold hover:bg-slate-900 transition cursor-pointer"
              >
                <Printer className="w-3.5 h-3.5" />
                Cetak Sticker Barcode
              </button>
            </div>

            {/* Specs Breakdown */}
            <div className="space-y-2 text-xs divide-y divide-slate-100">
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500">Nama User:</span>
                <span className="font-bold text-slate-800">{selectedDevice.name}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500">Jenis Perangkat:</span>
                <span className="font-semibold text-slate-800">{selectedDevice.type}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500">Tipe & Merek:</span>
                <span className="font-semibold text-slate-800">{selectedDevice.brand}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500">Lokasi:</span>
                <span className="font-semibold text-slate-800">{selectedDevice.description || '-'}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500">Serial Number:</span>
                <span className="font-mono font-semibold text-slate-800">{selectedDevice.sn || '-'}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500">Status Kondisi:</span>
                <span className={`font-bold px-2 py-0.5 rounded-full text-[10px] ${
                  selectedDevice.condition === 'Baik' ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'
                }`}>
                  {selectedDevice.condition}
                </span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500">Terakhir Pemeliharaan:</span>
                <span className="font-semibold text-slate-800">
                  {new Date(selectedDevice.lastMaintenance).toLocaleDateString('id-ID')}
                </span>
              </div>
            </div>

            <div className="pt-2 flex gap-2">
              <button
                onClick={() => {
                  setShowDetailModal(false);
                  if (onSelectDeviceForMaintenance) {
                    onSelectDeviceForMaintenance(selectedDevice.id);
                  }
                  onNavigate('batch_maintenance');
                }}
                className="flex-1 py-2 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl font-bold text-xs transition cursor-pointer"
              >
                Mulai Maintenance Unit Ini
              </button>
              <button
                onClick={() => setShowDetailModal(false)}
                className="px-4 py-2 bg-slate-100 text-slate-700 rounded-xl font-semibold text-xs hover:bg-slate-200 transition cursor-pointer"
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
