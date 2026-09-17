import React, { useState } from 'react';
import { 
  FileText, 
  Printer, 
  Download, 
  CheckSquare, 
  Square, 
  Camera, 
  AlertTriangle,
  Loader2,
  FileCheck,
  Calendar,
  Layers
} from 'lucide-react';
import { useMaintenance } from '../context/MaintenanceContext';
import { 
  generateOfficialChecklistPdf, 
  generateOfficialPhotoReportPdf,
  generateOfficialTroublePdf 
} from '../utils/pdfGenerator';

export const ReportScreen: React.FC = () => {
  const { devices, maintenanceLogs, troubleTickets } = useMaintenance();

  type ReportTab = 'checklist' | 'photos' | 'trouble';

  const [activeTab, setActiveTab] = useState<ReportTab>('checklist');
  const [selectedMonth, setSelectedMonth] = useState<number>(8); // September (0-indexed: 8)
  const [selectedYear, setSelectedYear] = useState<number>(2026);
  const [selectedDeviceType, setSelectedDeviceType] = useState<'Semua' | 'AIO' | 'Laptop'>('Laptop');
  const [isGeneratingPdf, setIsGeneratingPdf] = useState<boolean>(false);

  const months = [
    'Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni',
    'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember'
  ];

  // Filter maintenance logs
  const filteredLogs = maintenanceLogs.filter(log => {
    const d = new Date(log.timestamp);
    const matchesMonth = d.getMonth() === selectedMonth;
    const matchesYear = d.getFullYear() === selectedYear;
    const device = devices.find(dev => dev.id === log.deviceId);
    const matchesType = selectedDeviceType === 'Semua' || (device && device.type === selectedDeviceType);
    return matchesMonth && matchesYear && matchesType;
  });

  // Filter trouble tickets
  const filteredTickets = troubleTickets.filter(ticket => {
    const d = new Date(ticket.timestamp);
    const matchesMonth = d.getMonth() === selectedMonth;
    const matchesYear = d.getFullYear() === selectedYear;
    const device = devices.find(dev => dev.id === ticket.deviceId);
    const matchesType = selectedDeviceType === 'Semua' || (device && device.type === selectedDeviceType);
    return matchesMonth && matchesYear && matchesType;
  });

  // Title category for Checklist
  let titleCategory = 'LAPTOP DELL';
  if (selectedDeviceType === 'AIO') {
    titleCategory = 'PC / AIO DELL';
  } else if (selectedDeviceType === 'Semua') {
    titleCategory = 'PC / LAPTOP DELL';
  }

  // Print Handler
  const handlePrint = () => {
    window.print();
  };

  // Direct PDF Download Handler matching the exact Android output
  const handleDownloadPdf = async () => {
    try {
      setIsGeneratingPdf(true);

      if (activeTab === 'checklist') {
        await generateOfficialChecklistPdf(
          filteredLogs,
          devices,
          months[selectedMonth],
          selectedYear,
          selectedDeviceType
        );
      } else if (activeTab === 'photos') {
        await generateOfficialPhotoReportPdf(
          filteredLogs,
          devices,
          months[selectedMonth],
          selectedYear
        );
      } else if (activeTab === 'trouble') {
        await generateOfficialTroublePdf(
          filteredTickets,
          months[selectedMonth],
          selectedYear
        );
      }
    } catch (err) {
      console.error('Error generating PDF:', err);
      alert('Terjadi kendala saat membuat file PDF. Silakan gunakan tombol Cetak / Print A4.');
    } finally {
      setIsGeneratingPdf(false);
    }
  };

  // Export CSV Handler
  const handleExportCSV = () => {
    let csvContent = 'data:text/csv;charset=utf-8,';
    csvContent += 'No,Lokasi,User,S/N,Merk,Health Report,Disk Cleanup,Hardware Cleanup,Checking Drive Error,Scanning Virus,Checking Network,Updating Antivirus,Updating Applicat,Tanggal,Keterangan\n';

    filteredLogs.forEach((log, index) => {
      const dev = devices.find(d => d.id === log.deviceId);
      const dateStr = new Date(log.timestamp).toLocaleDateString('id-ID');
      csvContent += `${index + 1},"${dev?.description || '-'}","${log.deviceName}","${dev?.sn || dev?.serialNumber || '-'}","${dev?.brand || '-'}","${log.healthReport ? '1' : '0'}","${log.diskCleanup ? '1' : '0'}","${log.hardwareCleanup ? '1' : '0'}","${log.checkingDriveError ? '1' : '0'}","${log.scanningVirus ? '1' : '0'}","${log.checkingNetwork ? '1' : '0'}","${log.updatingAntivirus ? '1' : '0'}","${log.updatingAplikasi ? '1' : '0'}","${dateStr}","${log.notes || 'baik'}"\n`;
    });

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `Form_Checklist_${titleCategory.replace(/\s+/g, '_')}_${months[selectedMonth]}_${selectedYear}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // 8 Inspection Items for Photo Report
  const photoInspectionItems = [
    { id: 'hr', name: 'Health Report' },
    { id: 'dc', name: 'Disk CleanUp' },
    { id: 'hc', name: 'Hardware CleanUp' },
    { id: 'de', name: 'Checking Drive Error' },
    { id: 'sv', name: 'Scanning Virus' },
    { id: 'cn', name: 'Checking Network' },
    { id: 'av', name: 'Updating Antivirus Databases' },
    { id: 'ap', name: 'Updating Aplikasi' }
  ];

  return (
    <div className="space-y-6 pb-20">
      {/* Top Filter and Actions Bar (Hidden on Print) */}
      <div className="no-print space-y-4">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div>
            <div className="flex items-center gap-2.5">
              <span className="p-2.5 rounded-xl bg-blue-50 text-blue-700 border border-blue-100">
                <FileText className="w-5 h-5" />
              </span>
              <div>
                <h1 className="text-xl font-bold text-slate-800 tracking-tight">
                  Laporan Resmi PDF Pemeliharaan
                </h1>
                <p className="text-xs text-slate-500 mt-0.5">
                  Format baku Bandara Sultan Syarif Kasim II Pekanbaru (sesuai versi Android)
                </p>
              </div>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            <button
              onClick={handleExportCSV}
              className="inline-flex items-center gap-1.5 px-3.5 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold transition cursor-pointer"
            >
              <Download className="w-4 h-4" />
              Export CSV
            </button>
            <button
              onClick={handlePrint}
              className="inline-flex items-center gap-1.5 px-4 py-2.5 bg-slate-800 hover:bg-slate-900 text-white rounded-xl text-xs font-bold transition cursor-pointer"
            >
              <Printer className="w-4 h-4" />
              Cetak / Print A4
            </button>
            <button
              onClick={handleDownloadPdf}
              disabled={isGeneratingPdf}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-[#0F7D3A] hover:bg-[#0A5527] disabled:bg-slate-400 text-white rounded-xl text-xs font-bold shadow-md shadow-emerald-700/20 transition cursor-pointer"
            >
              {isGeneratingPdf ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Membuat PDF...
                </>
              ) : (
                <>
                  <FileCheck className="w-4 h-4" />
                  Unduh PDF Resmi (.pdf)
                </>
              )}
            </button>
          </div>
        </div>

        {/* Filter Controls Bar */}
        <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm space-y-4 text-xs">
          {/* Main Format Selector Tabs */}
          <div className="flex flex-wrap gap-2 bg-slate-100 p-1.5 rounded-xl">
            <button
              onClick={() => setActiveTab('checklist')}
              className={`px-4 py-2 rounded-lg font-bold transition cursor-pointer flex items-center gap-2 ${
                activeTab === 'checklist'
                  ? 'bg-white text-blue-900 shadow-sm'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-white/50'
              }`}
            >
              <FileText className="w-4 h-4 text-blue-600" />
              1. Form Checklist Maintenance (Landscape A4)
            </button>

            <button
              onClick={() => setActiveTab('photos')}
              className={`px-4 py-2 rounded-lg font-bold transition cursor-pointer flex items-center gap-2 ${
                activeTab === 'photos'
                  ? 'bg-white text-blue-900 shadow-sm'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-white/50'
              }`}
            >
              <Camera className="w-4 h-4 text-emerald-600" />
              2. Lampiran Foto Perawatan (Portrait A4)
            </button>

            <button
              onClick={() => setActiveTab('trouble')}
              className={`px-4 py-2 rounded-lg font-bold transition cursor-pointer flex items-center gap-2 ${
                activeTab === 'trouble'
                  ? 'bg-white text-blue-900 shadow-sm'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-white/50'
              }`}
            >
              <AlertTriangle className="w-4 h-4 text-amber-600" />
              3. Laporan Trouble Ticket (CM)
            </button>
          </div>

          {/* Month, Year, Device Filters */}
          <div className="flex flex-wrap items-center justify-between gap-4 pt-1">
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-1.5">
                <span className="text-slate-500 font-semibold">Bulan:</span>
                <select
                  value={selectedMonth}
                  onChange={e => setSelectedMonth(Number(e.target.value))}
                  className="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg font-bold text-slate-800 focus:outline-none cursor-pointer"
                >
                  {months.map((m, idx) => (
                    <option key={m} value={idx}>{m}</option>
                  ))}
                </select>
              </div>

              <div className="flex items-center gap-1.5">
                <span className="text-slate-500 font-semibold">Tahun:</span>
                <select
                  value={selectedYear}
                  onChange={e => setSelectedYear(Number(e.target.value))}
                  className="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg font-bold text-slate-800 focus:outline-none cursor-pointer"
                >
                  <option value={2025}>2025</option>
                  <option value={2026}>2026</option>
                  <option value={2027}>2027</option>
                </select>
              </div>

              <div className="flex items-center gap-1.5">
                <span className="text-slate-500 font-semibold">Kategori Perangkat:</span>
                <select
                  value={selectedDeviceType}
                  onChange={e => setSelectedDeviceType(e.target.value as 'Semua' | 'AIO' | 'Laptop')}
                  className="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg font-bold text-slate-800 focus:outline-none cursor-pointer"
                >
                  <option value="Laptop">Laptop Dell</option>
                  <option value="AIO">PC / AIO Dell</option>
                  <option value="Semua">Semua Perangkat (PC / Laptop)</option>
                </select>
              </div>
            </div>

            <div className="text-xs text-slate-500">
              Total Baris: <strong className="text-slate-800">{filteredLogs.length} unit</strong>
            </div>
          </div>
        </div>
      </div>

      {/* ==================================================================================================== */}
      {/* 1. DOCUMENT PREVIEW: FORM CHECKLIST MAINTENANCE (LANDSCAPE A4 MATCHING USER SCREENSHOT) */}
      {/* ==================================================================================================== */}
      {activeTab === 'checklist' && (
        <div className="bg-white p-4 sm:p-8 rounded-2xl border-2 border-blue-600 shadow-md max-w-7xl mx-auto overflow-x-auto print:border-blue-600 print:shadow-none print:p-4">
          {/* Header Title */}
          <div className="text-center mb-5">
            <h2 className="text-xs sm:text-sm font-bold text-black uppercase tracking-tight">
              FORM CHECKLIST MAINTENANCE {titleCategory} BANDARA SULTAN SYARIF KASIM II PEKANBARU BULAN {months[selectedMonth].toUpperCase()} TAHUN {selectedYear}
            </h2>
          </div>

          {/* Table */}
          <table className="w-full text-[10px] text-left border-collapse border border-black text-black">
            <thead>
              {/* Row 1 Header */}
              <tr className="bg-[#CEE5F6] border-b border-black text-center font-bold">
                <th rowSpan={2} className="p-1 border border-black w-8">No.</th>
                <th rowSpan={2} className="p-1 border border-black min-w-[130px]">Lokasi</th>
                <th rowSpan={2} className="p-1 border border-black min-w-[100px]">User</th>
                <th rowSpan={2} className="p-1 border border-black min-w-[80px]">S/N</th>
                <th rowSpan={2} className="p-1 border border-black min-w-[60px]">Merk</th>
                <th colSpan={8} className="p-1 border border-black">Data Maintenance</th>
                <th rowSpan={2} className="p-1 border border-black min-w-[80px]">Tanggal</th>
                <th rowSpan={2} className="p-1 border border-black min-w-[85px]">Paraf Petugas</th>
                <th rowSpan={2} className="p-1 border border-black min-w-[85px]">Paraf User</th>
                <th rowSpan={2} className="p-1 border border-black min-w-[80px]">Keterangan</th>
              </tr>
              {/* Row 2 Subheader for Data Maintenance */}
              <tr className="bg-[#CEE5F6] border-b border-black text-center font-bold text-[9px]">
                <th className="p-1 border border-black w-12">Health<br/>Report</th>
                <th className="p-1 border border-black w-12">Disk<br/>Cleanup</th>
                <th className="p-1 border border-black w-12">Hardware<br/>Cleanup</th>
                <th className="p-1 border border-black w-12">Checking<br/>Drive<br/>Error</th>
                <th className="p-1 border border-black w-12">Scanning<br/>Virus</th>
                <th className="p-1 border border-black w-12">Checking<br/>Network</th>
                <th className="p-1 border border-black w-12">Updating<br/>Antivirus</th>
                <th className="p-1 border border-black w-12">Updating<br/>Applicat.</th>
              </tr>
            </thead>
            <tbody>
              {filteredLogs.map((log, index) => {
                const dev = devices.find(d => d.id === log.deviceId);
                const dateObj = new Date(log.timestamp);
                const day = String(dateObj.getDate()).padStart(2, '0');
                const month = String(dateObj.getMonth() + 1).padStart(2, '0');
                const dateStr = `${day}/${month}/${dateObj.getFullYear()}`;

                return (
                  <tr key={log.id} className="border-b border-black text-center hover:bg-slate-50/50">
                    <td className="p-1 border border-black">{index + 1}</td>
                    <td className="p-1 border border-black text-left">{dev?.description || 'Kantor Admin API/GAGS'}</td>
                    <td className="p-1 border border-black">{log.deviceName.split('(')[0].trim()}</td>
                    <td className="p-1 border border-black font-mono text-[9px]">{dev?.sn || dev?.serialNumber || '-'}</td>
                    <td className="p-1 border border-black">{dev?.brand || 'Dell'}</td>

                    {/* 8 Checkbox Columns matching the exact Android appearance */}
                    <td className="p-1 border border-black">
                      <div className="flex items-center justify-center">
                        {log.healthReport ? (
                          <span className="inline-block border border-black w-3.5 h-3.5 leading-none text-center font-bold text-[10px]">✓</span>
                        ) : (
                          <span className="inline-block border border-black w-3.5 h-3.5" />
                        )}
                      </div>
                    </td>
                    <td className="p-1 border border-black">
                      <div className="flex items-center justify-center">
                        {log.diskCleanup ? (
                          <span className="inline-block border border-black w-3.5 h-3.5 leading-none text-center font-bold text-[10px]">✓</span>
                        ) : (
                          <span className="inline-block border border-black w-3.5 h-3.5" />
                        )}
                      </div>
                    </td>
                    <td className="p-1 border border-black">
                      <div className="flex items-center justify-center">
                        {log.hardwareCleanup ? (
                          <span className="inline-block border border-black w-3.5 h-3.5 leading-none text-center font-bold text-[10px]">✓</span>
                        ) : (
                          <span className="inline-block border border-black w-3.5 h-3.5" />
                        )}
                      </div>
                    </td>
                    <td className="p-1 border border-black">
                      <div className="flex items-center justify-center">
                        {log.checkingDriveError ? (
                          <span className="inline-block border border-black w-3.5 h-3.5 leading-none text-center font-bold text-[10px]">✓</span>
                        ) : (
                          <span className="inline-block border border-black w-3.5 h-3.5" />
                        )}
                      </div>
                    </td>
                    <td className="p-1 border border-black">
                      <div className="flex items-center justify-center">
                        {log.scanningVirus ? (
                          <span className="inline-block border border-black w-3.5 h-3.5 leading-none text-center font-bold text-[10px]">✓</span>
                        ) : (
                          <span className="inline-block border border-black w-3.5 h-3.5" />
                        )}
                      </div>
                    </td>
                    <td className="p-1 border border-black">
                      <div className="flex items-center justify-center">
                        {log.checkingNetwork ? (
                          <span className="inline-block border border-black w-3.5 h-3.5 leading-none text-center font-bold text-[10px]">✓</span>
                        ) : (
                          <span className="inline-block border border-black w-3.5 h-3.5" />
                        )}
                      </div>
                    </td>
                    <td className="p-1 border border-black">
                      <div className="flex items-center justify-center">
                        {log.updatingAntivirus ? (
                          <span className="inline-block border border-black w-3.5 h-3.5 leading-none text-center font-bold text-[10px]">✓</span>
                        ) : (
                          <span className="inline-block border border-black w-3.5 h-3.5" />
                        )}
                      </div>
                    </td>
                    <td className="p-1 border border-black">
                      <div className="flex items-center justify-center">
                        {log.updatingAplikasi ? (
                          <span className="inline-block border border-black w-3.5 h-3.5 leading-none text-center font-bold text-[10px]">✓</span>
                        ) : (
                          <span className="inline-block border border-black w-3.5 h-3.5" />
                        )}
                      </div>
                    </td>

                    <td className="p-1 border border-black text-[9px]">{dateStr}</td>

                    {/* Paraf Petugas Signature */}
                    <td className="p-1 border border-black">
                      <div className="h-6 flex items-center justify-center">
                        {log.techSignatureData ? (
                          <img src={log.techSignatureData} alt="Paraf Petugas" className="max-h-5 object-contain" />
                        ) : (
                          <svg width="40" height="20" viewBox="0 0 60 30" className="stroke-slate-800 fill-none stroke-[2]">
                            <path d="M5,20 Q20,2 35,18 T55,10" />
                          </svg>
                        )}
                      </div>
                    </td>

                    {/* Paraf User Signature */}
                    <td className="p-1 border border-black">
                      <div className="h-6 flex items-center justify-center">
                        {log.signatureData ? (
                          <img src={log.signatureData} alt="Paraf User" className="max-h-5 object-contain" />
                        ) : (
                          <svg width="40" height="20" viewBox="0 0 60 30" className="stroke-slate-800 fill-none stroke-[2]">
                            <path d="M10,25 Q30,5 50,22 T58,15" />
                          </svg>
                        )}
                      </div>
                    </td>

                    <td className="p-1 border border-black text-[9px]">{log.notes || 'baik'}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>

          {/* Bottom Signatures Block */}
          <div className="mt-16 grid grid-cols-2 text-center text-xs text-black">
            <div>
              <p>Pengawas,</p>
              <p className="font-bold">AIRPORT TECHNOLOGY ENGINEER</p>
              <div className="h-20" />
              <p className="font-bold underline tracking-wide">ARIFATUL AZHAR</p>
            </div>

            <div>
              <p>Pekanbaru, 10 September {selectedYear}</p>
              <p>Mengetahui,</p>
              <p className="font-bold">AIRPORT TECHNOLOGY DEPARTMENT HEAD</p>
              <div className="h-20" />
              <p className="font-bold underline tracking-wide">EKO ARIF RAHMANTO</p>
            </div>
          </div>
        </div>
      )}

      {/* ==================================================================================================== */}
      {/* 2. DOCUMENT PREVIEW: LAMPIRAN FOTO PERAWATAN (PORTRAIT A4 MATCHING USER SCREENSHOTS) */}
      {/* ==================================================================================================== */}
      {activeTab === 'photos' && (
        <div className="space-y-8 max-w-4xl mx-auto">
          {filteredLogs.map((log, logIdx) => {
            const dateObj = new Date(log.timestamp);
            const dayStr = String(dateObj.getDate()).padStart(2, '0');
            const dateTitle = `Tanggal : ${dayStr} ${months[selectedMonth]} ${selectedYear}`;

            const sampleImgBefore = log.healthReportBeforePhoto || 'https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=600&q=80';
            const sampleImgAfter = log.healthReportAfterPhoto || 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&q=80';

            return (
              <div key={log.id} className="space-y-8">
                {/* PAGE 1: Items 1 to 4 */}
                <div className="bg-white p-6 sm:p-10 rounded-2xl border border-slate-300 shadow-md page-break">
                  <div className="text-center mb-5">
                    <h2 className="text-sm font-bold text-black uppercase tracking-tight">
                      LAMPIRAN FOTO PERAWATAN LAPTOP & PC AIO
                    </h2>
                    <p className="text-xs text-black mt-1 font-medium">
                      {dateTitle}
                    </p>
                  </div>

                  <table className="w-full text-xs text-left border-collapse border border-black text-black">
                    <thead>
                      <tr className="bg-white border-b border-black text-center font-bold">
                        <th className="p-2 border border-black w-1/3">JENIS PEMERIKSAAN</th>
                        <th className="p-2 border border-black w-1/3">SEBELUM</th>
                        <th className="p-2 border border-black w-1/3">SESUDAH</th>
                      </tr>
                    </thead>
                    <tbody>
                      {photoInspectionItems.slice(0, 4).map(item => (
                        <tr key={item.id} className="border-b border-black">
                          <td className="p-3 border border-black text-center font-medium">
                            {item.name}
                          </td>
                          <td className="p-1 border border-black text-center">
                            <img 
                              src={sampleImgBefore} 
                              alt="Sebelum" 
                              className="w-full h-36 object-cover rounded" 
                            />
                          </td>
                          <td className="p-1 border border-black text-center">
                            <img 
                              src={sampleImgAfter} 
                              alt="Sesudah" 
                              className="w-full h-36 object-cover rounded" 
                            />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* PAGE 2: Items 5 to 8 */}
                <div className="bg-white p-6 sm:p-10 rounded-2xl border border-slate-300 shadow-md page-break">
                  <table className="w-full text-xs text-left border-collapse border border-black text-black">
                    <thead>
                      <tr className="bg-white border-b border-black text-center font-bold">
                        <th className="p-2 border border-black w-1/3">JENIS PEMERIKSAAN</th>
                        <th className="p-2 border border-black w-1/3">SEBELUM</th>
                        <th className="p-2 border border-black w-1/3">SESUDAH</th>
                      </tr>
                    </thead>
                    <tbody>
                      {photoInspectionItems.slice(4, 8).map(item => (
                        <tr key={item.id} className="border-b border-black">
                          <td className="p-3 border border-black text-center font-medium">
                            {item.name}
                          </td>
                          <td className="p-1 border border-black text-center">
                            <img 
                              src={sampleImgBefore} 
                              alt="Sebelum" 
                              className="w-full h-36 object-cover rounded" 
                            />
                          </td>
                          <td className="p-1 border border-black text-center">
                            <img 
                              src={sampleImgAfter} 
                              alt="Sesudah" 
                              className="w-full h-36 object-cover rounded" 
                            />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* ==================================================================================================== */}
      {/* 3. DOCUMENT PREVIEW: LAPORAN TROUBLE TICKET (CM) */}
      {/* ==================================================================================================== */}
      {activeTab === 'trouble' && (
        <div className="bg-white p-6 sm:p-10 rounded-2xl border-2 border-blue-600 shadow-md max-w-6xl mx-auto">
          <div className="text-center mb-6">
            <h2 className="text-sm font-bold text-black uppercase tracking-tight">
              LAPORAN PENANGANAN GANGGUAN KOMPUTER (CORRECTIVE MAINTENANCE) BANDARA SSK II {months[selectedMonth].toUpperCase()} {selectedYear}
            </h2>
          </div>

          <table className="w-full text-xs text-left border-collapse border border-black text-black">
            <thead>
              <tr className="bg-[#CEE5F6] border-b border-black text-center font-bold">
                <th className="p-2 border border-black w-10">No</th>
                <th className="p-2 border border-black">Perangkat & Merk</th>
                <th className="p-2 border border-black">Pelapor / Unit</th>
                <th className="p-2 border border-black text-center">Tanggal</th>
                <th className="p-2 border border-black">Gejala Kerusakan</th>
                <th className="p-2 border border-black">Tindakan Perbaikan</th>
                <th className="p-2 border border-black text-center">Durasi</th>
                <th className="p-2 border border-black text-center">Status</th>
              </tr>
            </thead>
            <tbody>
              {filteredTickets.map((ticket, idx) => (
                <tr key={ticket.id} className="border-b border-black">
                  <td className="p-2 border border-black text-center">{idx + 1}</td>
                  <td className="p-2 border border-black font-semibold">{ticket.deviceName}</td>
                  <td className="p-2 border border-black">{ticket.reportedBy}</td>
                  <td className="p-2 border border-black text-center whitespace-nowrap">
                    {new Date(ticket.timestamp).toLocaleDateString('id-ID')}
                  </td>
                  <td className="p-2 border border-black">{ticket.description}</td>
                  <td className="p-2 border border-black">{ticket.actionTaken || '-'}</td>
                  <td className="p-2 border border-black text-center">{ticket.duration || '30 Menit'}</td>
                  <td className="p-2 border border-black text-center font-bold">
                    <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 text-[10px]">
                      {ticket.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Bottom Signatures Block */}
          <div className="mt-16 grid grid-cols-2 text-center text-xs text-black">
            <div>
              <p>Pengawas,</p>
              <p className="font-bold">AIRPORT TECHNOLOGY ENGINEER</p>
              <div className="h-20" />
              <p className="font-bold underline tracking-wide">ARIFATUL AZHAR</p>
            </div>

            <div>
              <p>Pekanbaru, 10 September {selectedYear}</p>
              <p>Mengetahui,</p>
              <p className="font-bold">AIRPORT TECHNOLOGY DEPARTMENT HEAD</p>
              <div className="h-20" />
              <p className="font-bold underline tracking-wide">EKO ARIF RAHMANTO</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
