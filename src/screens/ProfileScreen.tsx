import React, { useState, useRef } from 'react';
import { 
  Settings, 
  Plane, 
  Building2, 
  Database, 
  Download, 
  Upload, 
  RotateCcw, 
  ShieldCheck, 
  CheckCircle2, 
  Info,
  Layers,
  Server,
  Key,
  Cloud,
  RefreshCw,
  Activity,
  HardDrive,
  Check,
  AlertCircle
} from 'lucide-react';
import { useMaintenance } from '../context/MaintenanceContext';
import { testSupabaseConnection, SUPABASE_URL } from '../services/supabaseClient';
import { testR2Connection, R2_WORKER_URL } from '../services/r2Storage';

export const ProfileScreen: React.FC = () => {
  const { 
    devices, 
    technicians, 
    troubleTickets, 
    maintenanceLogs, 
    uploadedFiles,
    isCloudConnected,
    isSyncing,
    lastSyncTime,
    syncError,
    refreshFromCloud,
    exportBackupJson, 
    importBackupJson, 
    resetToInitialData 
  } = useMaintenance();

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [syncStatus, setSyncStatus] = useState<string | null>(null);

  // Cloud Diagnostics state
  const [isTestingCloud, setIsTestingCloud] = useState(false);
  const [cloudTestResult, setCloudTestResult] = useState<{
    supabaseOk: boolean;
    supabaseMsg: string;
    r2Ok: boolean;
    r2Msg: string;
  } | null>(null);

  const handleTestCloud = async () => {
    setIsTestingCloud(true);
    setCloudTestResult(null);

    try {
      const [supaRes, r2Res] = await Promise.all([
        testSupabaseConnection(),
        testR2Connection()
      ]);

      setCloudTestResult({
        supabaseOk: supaRes.success,
        supabaseMsg: supaRes.message + (supaRes.deviceCount !== undefined ? ` (${supaRes.deviceCount} perangkat di server)` : ''),
        r2Ok: r2Res.success,
        r2Msg: r2Res.message
      });
    } catch (err: any) {
      setCloudTestResult({
        supabaseOk: false,
        supabaseMsg: 'Koneksi gagal',
        r2Ok: false,
        r2Msg: err?.message || 'Koneksi R2 gagal'
      });
    } finally {
      setIsTestingCloud(false);
    }
  };

  const handleManualSync = async () => {
    await refreshFromCloud();
    setSyncStatus('Sinkronisasi data dengan Supabase & Cloudflare R2 berhasil!');
    setTimeout(() => setSyncStatus(null), 3500);
  };

  const handleExport = () => {
    const data = exportBackupJson();
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `backup_cms_bandara_ssk_ii_${new Date().toISOString().split('T')[0]}.json`;
    a.click();
    URL.revokeObjectURL(url);
    setSyncStatus('Backup data berhasil diunduh!');
    setTimeout(() => setSyncStatus(null), 3000);
  };

  const handleImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result as string;
      const success = importBackupJson(content);
      if (success) {
        setSyncStatus('Data berhasil dipulihkan dari file backup!');
      } else {
        alert('Gagal membaca file backup. Pastikan format file JSON valid.');
      }
      setTimeout(() => setSyncStatus(null), 3000);
    };
    reader.readAsText(file);
  };

  const handleReset = () => {
    if (window.confirm('PERINGATAN: Apakah Anda yakin ingin mereset seluruh database ke data awal? Semua perubahan data kustom akan diganti.')) {
      resetToInitialData();
      setSyncStatus('Database berhasil direset ke data awal demo!');
      setTimeout(() => setSyncStatus(null), 3000);
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-16">
      {/* Airport Profile Banner */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="h-28 bg-gradient-to-r from-[#0A5527] to-[#0F7D3A] relative">
          <div className="absolute top-4 right-4 text-white/20">
            <Plane className="w-24 h-24" />
          </div>
        </div>

        <div className="px-6 pb-6 pt-0 relative">
          <div className="flex flex-col sm:flex-row sm:items-end gap-4 -mt-12 mb-4">
            <div className="w-24 h-24 rounded-2xl bg-white p-2 border-4 border-white shadow-md overflow-hidden">
              <img 
                src="/airport_logo.jpg" 
                alt="Logo Bandara SSK II" 
                className="w-full h-full object-contain"
                onError={(e) => {
                  (e.target as HTMLImageElement).src = '/logo.jpg';
                }}
              />
            </div>
            <div className="space-y-1">
              <h1 className="text-xl sm:text-2xl font-extrabold text-slate-900">
                Bandara Internasional Sultan Syarif Kasim II
              </h1>
              <p className="text-xs sm:text-sm font-semibold text-[#0F7D3A]">
                Unit Airport Technology & Sistem Informasi (PKU)
              </p>
            </div>
          </div>

          <p className="text-xs sm:text-sm text-slate-600 leading-relaxed">
            Sistem Informasi Monitoring dan Pemeliharaan Komputer (CMS) dirancang untuk memfasilitasi 
            pencatatan pemeliharaan preventif (PM) rutin, tindak lanjut penanganan gangguan trouble ticket (CM), 
            dokumentasi foto penyerahan, serta digitalisasi tanda tangan staf operasional di lingkungan bandara.
          </p>
        </div>
      </div>

      {syncStatus && (
        <div className="p-4 bg-emerald-50 border border-emerald-300 rounded-2xl text-emerald-800 text-xs sm:text-sm font-semibold flex items-center gap-2">
          <CheckCircle2 className="w-5 h-5 text-emerald-600" />
          {syncStatus}
        </div>
      )}

      {/* Database Statistics Cards */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm space-y-4">
        <h2 className="text-base font-bold text-slate-800 flex items-center gap-2 border-b border-slate-100 pb-3">
          <Database className="w-4 h-4 text-[#0F7D3A]" />
          Statistik Database Sistem
        </h2>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-center">
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80">
            <span className="text-[11px] font-bold text-slate-400 uppercase">Total Perangkat</span>
            <p className="text-2xl font-extrabold text-[#0F7D3A] mt-1">{devices.length}</p>
          </div>
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80">
            <span className="text-[11px] font-bold text-slate-400 uppercase">Log Maintenance</span>
            <p className="text-2xl font-extrabold text-blue-600 mt-1">{maintenanceLogs.length}</p>
          </div>
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80">
            <span className="text-[11px] font-bold text-slate-400 uppercase">Trouble Tickets</span>
            <p className="text-2xl font-extrabold text-amber-600 mt-1">{troubleTickets.length}</p>
          </div>
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80">
            <span className="text-[11px] font-bold text-slate-400 uppercase">Tim Teknisi</span>
            <p className="text-2xl font-extrabold text-indigo-600 mt-1">{technicians.length}</p>
          </div>
        </div>
      </div>

      {/* Cloud Integration Status & Diagnostics (Supabase & Cloudflare R2) */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-3">
          <h2 className="text-base font-bold text-slate-800 flex items-center gap-2">
            <Cloud className="w-5 h-5 text-[#0F7D3A]" />
            Integrasi Cloud Database & Penyimpanan Berkas
          </h2>
          <div className="flex items-center gap-2">
            <button
              onClick={handleManualSync}
              disabled={isSyncing}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-emerald-50 hover:bg-emerald-100 text-[#0F7D3A] rounded-xl text-xs font-bold transition border border-emerald-200 cursor-pointer disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isSyncing ? 'animate-spin' : ''}`} />
              {isSyncing ? 'Menyinkronkan...' : 'Sinkronkan Sekarang'}
            </button>
            <button
              onClick={handleTestCloud}
              disabled={isTestingCloud}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl text-xs font-bold transition shadow-sm cursor-pointer disabled:opacity-50"
            >
              <Activity className={`w-3.5 h-3.5 ${isTestingCloud ? 'animate-spin' : ''}`} />
              {isTestingCloud ? 'Menguji...' : 'Tes Koneksi Cloud'}
            </button>
          </div>
        </div>

        {/* Status Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Supabase Card */}
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
                <Database className="w-4 h-4 text-emerald-600" />
                Supabase PostgreSQL
              </span>
              <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold ${
                isCloudConnected ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
              }`}>
                {isCloudConnected ? (
                  <>
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-600 animate-pulse"></span>
                    Terhubung
                  </>
                ) : (
                  <>
                    <span className="w-1.5 h-1.5 rounded-full bg-amber-600"></span>
                    Offline Mode
                  </>
                )}
              </span>
            </div>
            <p className="text-[11px] text-slate-500 font-mono truncate">
              {SUPABASE_URL}
            </p>
            <div className="text-[11px] text-slate-600 flex justify-between pt-1 border-t border-slate-200/60">
              <span>Tabel Sinkron:</span>
              <span className="font-semibold text-slate-700">devices, logs, tickets, files</span>
            </div>
          </div>

          {/* Cloudflare R2 Worker Card */}
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
                <HardDrive className="w-4 h-4 text-orange-500" />
                Cloudflare R2 Storage Worker
              </span>
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-600 animate-pulse"></span>
                Endpoint Aktif
              </span>
            </div>
            <p className="text-[11px] text-slate-500 font-mono truncate">
              {R2_WORKER_URL}
            </p>
            <div className="text-[11px] text-slate-600 flex justify-between pt-1 border-t border-slate-200/60">
              <span>Fitur:</span>
              <span className="font-semibold text-slate-700">Upload Foto, Dokumen BA, PDF</span>
            </div>
          </div>
        </div>

        {/* Diagnostic Test Output */}
        {cloudTestResult && (
          <div className="p-3.5 bg-slate-100 rounded-xl text-xs space-y-1.5 border border-slate-200 animate-fadeIn">
            <p className="font-bold text-slate-800">Hasil Pengujian Koneksi Cloud:</p>
            <div className="flex items-center gap-2">
              {cloudTestResult.supabaseOk ? (
                <Check className="w-4 h-4 text-emerald-600 shrink-0" />
              ) : (
                <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
              )}
              <span className={cloudTestResult.supabaseOk ? 'text-emerald-800 font-medium' : 'text-rose-700'}>
                <strong>Supabase:</strong> {cloudTestResult.supabaseMsg}
              </span>
            </div>
            <div className="flex items-center gap-2">
              {cloudTestResult.r2Ok ? (
                <Check className="w-4 h-4 text-emerald-600 shrink-0" />
              ) : (
                <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
              )}
              <span className={cloudTestResult.r2Ok ? 'text-emerald-800 font-medium' : 'text-rose-700'}>
                <strong>Cloudflare R2:</strong> {cloudTestResult.r2Msg}
              </span>
            </div>
          </div>
        )}

        {lastSyncTime && (
          <p className="text-[11px] text-slate-400">
            Terakhir disinkronkan: {lastSyncTime.toLocaleTimeString('id-ID')} ({lastSyncTime.toLocaleDateString('id-ID')})
          </p>
        )}
      </div>

      {/* Backup & Restore Tools */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm space-y-4">
        <h2 className="text-base font-bold text-slate-800 flex items-center gap-2 border-b border-slate-100 pb-3">
          <Server className="w-4 h-4 text-[#0F7D3A]" />
          Manajemen Cadangan & Pemulihan (Backup & Restore)
        </h2>

        <p className="text-xs text-slate-500">
          Simpan seluruh database perangkat, log pemeliharaan, tiket, dan teknisi ke file format JSON offline, atau pulihkan data dari file cadangan sebelumnya.
        </p>

        <div className="flex flex-wrap gap-3 pt-2">
          <button
            onClick={handleExport}
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl text-xs font-bold transition shadow-sm cursor-pointer"
          >
            <Download className="w-4 h-4" />
            Unduh Cadangan JSON
          </button>

          <button
            onClick={() => fileInputRef.current?.click()}
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold transition cursor-pointer"
          >
            <Upload className="w-4 h-4" />
            Pulihkan Dari File JSON
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".json"
            className="hidden"
            onChange={handleImport}
          />

          <button
            onClick={handleReset}
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-rose-50 hover:bg-rose-100 text-rose-700 rounded-xl text-xs font-bold transition border border-rose-200 ml-auto cursor-pointer"
          >
            <RotateCcw className="w-4 h-4" />
            Reset Data Ke Awal
          </button>
        </div>
      </div>
    </div>
  );
};
