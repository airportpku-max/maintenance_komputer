import React from 'react';
import { 
  Monitor, 
  Laptop, 
  CheckCircle2, 
  AlertCircle, 
  Wrench, 
  Database, 
  FolderUp, 
  FileText, 
  AlertTriangle, 
  CheckSquare, 
  QrCode, 
  ArrowRight,
  TrendingUp,
  Clock,
  ShieldCheck,
  Plane
} from 'lucide-react';
import { ScreenRoute } from '../types';
import { useMaintenance } from '../context/MaintenanceContext';

interface DashboardScreenProps {
  onNavigate: (route: ScreenRoute) => void;
}

export const DashboardScreen: React.FC<DashboardScreenProps> = ({ onNavigate }) => {
  const { devices, troubleTickets, maintenanceLogs, isDeviceAlreadyMaintainedInMonth } = useMaintenance();

  const totalAIO = devices.filter(d => d.type === 'AIO').length;
  const totalLaptop = devices.filter(d => d.type === 'Laptop').length;
  const totalDevices = devices.length;

  const totalTrouble = devices.filter(d => d.condition === 'Trouble').length;
  const totalBaik = devices.filter(d => d.condition === 'Baik').length;

  const activeTicketsCount = troubleTickets.filter(t => t.status === 'Pending').length;
  const maintainedDevicesCount = devices.filter(d => isDeviceAlreadyMaintainedInMonth(d)).length;
  const remainingDevicesCount = Math.max(0, totalDevices - maintainedDevicesCount);
  const progressPercent = totalDevices > 0 ? Math.round((maintainedDevicesCount / totalDevices) * 100) : 0;

  const recentLogs = [...maintenanceLogs].sort((a, b) => b.timestamp - a.timestamp).slice(0, 4);
  const recentTickets = [...troubleTickets].sort((a, b) => b.timestamp - a.timestamp).slice(0, 3);

  return (
    <div className="space-y-6 pb-12">
      {/* Hero Banner with Airport Theme */}
      <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-[#0A5527] via-[#0F7D3A] to-[#15803d] text-white p-6 sm:p-8 shadow-xl">
        <div className="absolute top-0 right-0 -mr-16 -mt-16 w-64 h-64 bg-white/5 rounded-full blur-2xl pointer-events-none" />
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div className="space-y-2 max-w-2xl">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/15 text-emerald-100 text-xs font-semibold backdrop-blur-sm border border-white/20">
              <Plane className="w-3.5 h-3.5" />
              Bandara Internasional Sultan Syarif Kasim II (PKU)
            </div>
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
              Dashboard Pemeliharaan Komputer
            </h1>
            <p className="text-emerald-100/90 text-sm leading-relaxed">
              Monitoring berkala kondisi fisik, software, hardware, antivirus, dan penanganan trouble ticket AIO PC & Laptop bandara secara real-time.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={() => onNavigate('batch_maintenance')}
              className="inline-flex items-center gap-2 px-4 py-2.5 bg-white text-[#0A5527] hover:bg-emerald-50 rounded-xl font-bold text-xs sm:text-sm shadow-md transition transform active:scale-95 cursor-pointer"
            >
              <CheckSquare className="w-4 h-4 text-[#0A5527]" />
              Input Maintenance Praktis
            </button>
            <button
              onClick={() => onNavigate('scanner')}
              className="inline-flex items-center gap-2 px-4 py-2.5 bg-emerald-800/80 hover:bg-emerald-800 text-white rounded-xl font-semibold text-xs sm:text-sm border border-white/20 transition cursor-pointer"
            >
              <QrCode className="w-4 h-4" />
              Scan Barcode
            </button>
          </div>
        </div>
      </div>

      {/* Floating Summary Card (Identical to Kotlin App) */}
      <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-200">
        <div className="flex items-center gap-3 pb-4 border-b border-slate-100">
          <div className="w-10 h-10 rounded-xl bg-emerald-100 flex items-center justify-center text-[#0F7D3A]">
            <Wrench className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-800">
              Computer Maintenance System
            </h2>
            <p className="text-xs text-slate-500">
              Status real-time perangkat & aktifitas teknisi bulan ini
            </p>
          </div>
        </div>

        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-5">
          {/* Total Perangkat */}
          <div className="bg-slate-50 p-4 rounded-xl border border-slate-200/80 flex justify-between items-center">
            <div>
              <p className="text-[11px] font-bold tracking-wider uppercase text-slate-400">
                TOTAL PERANGKAT
              </p>
              <p className="text-3xl font-extrabold text-[#0F7D3A] mt-1">
                {totalDevices} <span className="text-sm font-semibold text-slate-600">Unit</span>
              </p>
              <div className="flex items-center gap-3 mt-2 text-xs font-medium text-slate-600">
                <span className="inline-flex items-center gap-1">
                  <Monitor className="w-3.5 h-3.5 text-blue-600" /> AIO: <strong className="text-slate-800">{totalAIO}</strong>
                </span>
                <span className="text-slate-300">|</span>
                <span className="inline-flex items-center gap-1">
                  <Laptop className="w-3.5 h-3.5 text-indigo-600" /> Laptop: <strong className="text-slate-800">{totalLaptop}</strong>
                </span>
              </div>
            </div>
            <div className="w-12 h-12 rounded-xl bg-emerald-100/60 flex items-center justify-center text-[#0F7D3A]">
              <Database className="w-6 h-6" />
            </div>
          </div>

          {/* Kondisi Perangkat */}
          <div className="bg-slate-50 p-4 rounded-xl border border-slate-200/80 flex justify-between items-center">
            <div>
              <p className="text-[11px] font-bold tracking-wider uppercase text-slate-400">
                KONDISI PERANGKAT
              </p>
              <div className="flex items-center gap-4 mt-2">
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
                  <span className="text-sm font-bold text-slate-800">{totalBaik} Baik</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-amber-500" />
                  <span className="text-sm font-bold text-amber-600">{totalTrouble} Trouble</span>
                </div>
              </div>
              <p className="text-xs text-slate-500 mt-2">
                Aktif Trouble Ticket: {' '}
                <span className={`font-bold ${activeTicketsCount > 0 ? 'text-red-600' : 'text-slate-700'}`}>
                  {activeTicketsCount} tiket
                </span>
              </p>
            </div>
            <div className="w-12 h-12 rounded-xl bg-amber-100/60 flex items-center justify-center text-amber-600">
              <AlertTriangle className="w-6 h-6" />
            </div>
          </div>
        </div>

        {/* 2nd Row: Maintenance Progress */}
        <div className="mt-5 pt-5 border-t border-slate-100 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-1">
            <p className="text-[11px] font-bold tracking-wider uppercase text-slate-400">
              AKTIFITAS MAINTENANCE BULAN INI
            </p>
            <p className="text-sm font-bold text-slate-800">
              {maintainedDevicesCount} dari {totalDevices} Unit Selesai Dikerjakan ({progressPercent}%)
            </p>
            <p className="text-xs text-slate-500">
              {remainingDevicesCount > 0 
                ? `Tersisa ${remainingDevicesCount} perangkat lagi yang perlu dikerjakan bulan ini.`
                : 'Semua perangkat telah selesai dilakukan pemeliharaan preventif bulan ini.'}
            </p>
          </div>

          <div className="w-full md:w-64 space-y-1.5">
            <div className="flex justify-between text-xs font-semibold text-slate-600">
              <span>Progress PM</span>
              <span className="text-[#0F7D3A]">{progressPercent}%</span>
            </div>
            <div className="w-full h-3 bg-slate-100 rounded-full overflow-hidden border border-slate-200">
              <div 
                className="h-full bg-gradient-to-r from-[#0F7D3A] to-emerald-500 transition-all duration-500 rounded-full"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Grid Menu Cards (From Android App 3-row layout) */}
      <div className="space-y-3">
        <h2 className="text-base font-bold text-slate-800 flex items-center gap-2">
          <span>Menu Navigasi Cepat</span>
        </h2>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 sm:gap-4">
          {/* Data Master */}
          <button
            onClick={() => onNavigate('data_master')}
            className="flex flex-col text-left p-4 rounded-xl bg-white hover:bg-emerald-50/50 border border-slate-200 hover:border-emerald-300 transition shadow-sm group cursor-pointer"
          >
            <div className="w-10 h-10 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center mb-3 group-hover:scale-110 transition">
              <Database className="w-5 h-5" />
            </div>
            <span className="font-bold text-slate-800 text-sm">Data Master</span>
            <span className="text-xs text-slate-400 mt-0.5">AIO PC & Laptop</span>
          </button>

          {/* Berkas */}
          <button
            onClick={() => onNavigate('upload_file')}
            className="flex flex-col text-left p-4 rounded-xl bg-white hover:bg-emerald-50/50 border border-slate-200 hover:border-emerald-300 transition shadow-sm group cursor-pointer"
          >
            <div className="w-10 h-10 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center mb-3 group-hover:scale-110 transition">
              <FolderUp className="w-5 h-5" />
            </div>
            <span className="font-bold text-slate-800 text-sm">Berkas & BA</span>
            <span className="text-xs text-slate-400 mt-0.5">Upload Dokumen</span>
          </button>

          {/* Laporan */}
          <button
            onClick={() => onNavigate('report')}
            className="flex flex-col text-left p-4 rounded-xl bg-white hover:bg-emerald-50/50 border border-slate-200 hover:border-emerald-300 transition shadow-sm group cursor-pointer"
          >
            <div className="w-10 h-10 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center mb-3 group-hover:scale-110 transition">
              <FileText className="w-5 h-5" />
            </div>
            <span className="font-bold text-slate-800 text-sm">Laporan</span>
            <span className="text-xs text-slate-400 mt-0.5">Cetak PDF / A4</span>
          </button>

          {/* Trouble Tickets */}
          <button
            onClick={() => onNavigate('trouble')}
            className="flex flex-col text-left p-4 rounded-xl bg-white hover:bg-rose-50/50 border border-slate-200 hover:border-rose-300 transition shadow-sm group cursor-pointer relative"
          >
            {activeTicketsCount > 0 && (
              <span className="absolute top-3 right-3 bg-red-500 text-white text-[10px] font-bold px-2 py-0.5 rounded-full">
                {activeTicketsCount}
              </span>
            )}
            <div className="w-10 h-10 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center mb-3 group-hover:scale-110 transition">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <span className="font-bold text-slate-800 text-sm">Trouble</span>
            <span className="text-xs text-slate-400 mt-0.5">Tiket Kerusakan</span>
          </button>

          {/* Input Maintenance Praktis */}
          <button
            onClick={() => onNavigate('batch_maintenance')}
            className="flex flex-col text-left p-4 rounded-xl bg-white hover:bg-emerald-50/50 border border-slate-200 hover:border-emerald-300 transition shadow-sm group cursor-pointer"
          >
            <div className="w-10 h-10 rounded-lg bg-emerald-50 text-[#0F7D3A] flex items-center justify-center mb-3 group-hover:scale-110 transition">
              <CheckSquare className="w-5 h-5" />
            </div>
            <span className="font-bold text-slate-800 text-sm">Input Main.</span>
            <span className="text-xs text-slate-400 mt-0.5">Praktis 8 Ceklis</span>
          </button>
        </div>
      </div>

      {/* Two Column Section: Trouble Tickets & Recent Maintenance Activity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Active Trouble Tickets */}
        <div className="bg-white rounded-2xl p-5 border border-slate-200 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertCircle className="w-5 h-5 text-amber-500" />
              <h3 className="font-bold text-slate-800 text-base">Tiket Trouble Terkini</h3>
            </div>
            <button
              onClick={() => onNavigate('trouble')}
              className="text-xs font-semibold text-[#0F7D3A] hover:underline flex items-center gap-1 cursor-pointer"
            >
              Lihat Semua <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="space-y-3">
            {recentTickets.length === 0 ? (
              <div className="p-8 text-center text-slate-400 text-xs">
                Tidak ada tiket trouble saat ini.
              </div>
            ) : (
              recentTickets.map(ticket => (
                <div 
                  key={ticket.id}
                  className="p-3.5 rounded-xl border border-slate-100 hover:border-slate-300 transition bg-slate-50/50 flex flex-col gap-2"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-xs text-slate-800">{ticket.deviceName}</span>
                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                      ticket.status === 'Pending' ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'
                    }`}>
                      {ticket.status}
                    </span>
                  </div>
                  <p className="text-xs text-slate-600 line-clamp-2">
                    {ticket.description}
                  </p>
                  <div className="flex items-center justify-between text-[11px] text-slate-400 pt-1 border-t border-slate-100">
                    <span>Pelapor: {ticket.reportedBy}</span>
                    <span>{new Date(ticket.timestamp).toLocaleDateString('id-ID')}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Recent Maintenance Logs */}
        <div className="bg-white rounded-2xl p-5 border border-slate-200 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <ShieldCheck className="w-5 h-5 text-[#0F7D3A]" />
              <h3 className="font-bold text-slate-800 text-base">Log Pemeliharaan Terbaru</h3>
            </div>
            <button
              onClick={() => onNavigate('report')}
              className="text-xs font-semibold text-[#0F7D3A] hover:underline flex items-center gap-1 cursor-pointer"
            >
              Buka Laporan <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="space-y-3">
            {recentLogs.length === 0 ? (
              <div className="p-8 text-center text-slate-400 text-xs">
                Belum ada log pemeliharaan yang tercatat.
              </div>
            ) : (
              recentLogs.map(log => (
                <div 
                  key={log.id}
                  className="p-3.5 rounded-xl border border-slate-100 hover:border-slate-300 transition bg-slate-50/50 space-y-1.5"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-xs text-slate-800">{log.deviceName}</span>
                    <span className="text-[11px] text-slate-400">
                      {new Date(log.timestamp).toLocaleDateString('id-ID')}
                    </span>
                  </div>
                  <p className="text-xs text-slate-600 line-clamp-2">
                    {log.actionTaken}
                  </p>
                  <div className="flex items-center gap-2 text-[11px] text-emerald-700 font-medium pt-1">
                    <Wrench className="w-3 h-3" />
                    <span>Teknisi: {log.technicianName}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
