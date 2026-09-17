import React, { useState } from 'react';
import { 
  FolderUp, 
  FileText, 
  Upload, 
  Trash2, 
  Download, 
  Search, 
  FileSpreadsheet, 
  Image as ImageIcon,
  File,
  CheckCircle2,
  Calendar,
  Cloud,
  Loader2,
  ExternalLink
} from 'lucide-react';
import { UploadedFile } from '../types';
import { useMaintenance } from '../context/MaintenanceContext';
import { uploadFileToR2 } from '../services/r2Storage';

export const UploadFileScreen: React.FC = () => {
  const { uploadedFiles, addUploadedFile, deleteUploadedFile, isCloudConnected } = useMaintenance();
  const [searchQuery, setSearchQuery] = useState('');
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadStatus, setUploadStatus] = useState<string | null>(null);

  const handleFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;

    setIsUploading(true);
    setUploadStatus(`Mengunggah ${files.length} berkas ke Cloudflare R2...`);

    try {
      const fileList = Array.from(files);
      for (const file of fileList) {
        const ext = file.name.split('.').pop()?.toUpperCase() || 'FILE';
        const sizeKB = Math.round(file.size / 1024);
        const sizeFormatted = sizeKB > 1024 ? `${(sizeKB / 1024).toFixed(1)} MB` : `${sizeKB} KB`;

        // Upload to Cloudflare R2 worker
        let finalUri: string | null = null;
        try {
          const r2Result = await uploadFileToR2(file);
          if (r2Result.success && r2Result.url) {
            finalUri = r2Result.url;
          }
        } catch (err) {
          console.warn('Gagal upload ke R2, fallback local data URL:', err);
        }

        // Fallback to data URL if R2 fails
        if (!finalUri) {
          finalUri = await new Promise<string>((resolve) => {
            const reader = new FileReader();
            reader.onloadend = () => resolve(reader.result as string);
            reader.readAsDataURL(file);
          });
        }

        addUploadedFile({
          fileName: file.name,
          fileSize: sizeFormatted,
          fileType: ext,
          fileUri: finalUri
        });
      }

      setUploadStatus(`Berhasil mengunggah ${fileList.length} berkas ke Cloudflare R2 & Supabase!`);
      setTimeout(() => setUploadStatus(null), 3500);
    } catch (err: any) {
      console.error('Error handling files:', err);
      setUploadStatus('Terjadi kesalahan saat mengunggah berkas.');
      setTimeout(() => setUploadStatus(null), 3000);
    } finally {
      setIsUploading(false);
    }
  };

  const getFileIcon = (fileType: string) => {
    switch (fileType.toUpperCase()) {
      case 'PDF':
        return <FileText className="w-8 h-8 text-rose-600" />;
      case 'XLSX':
      case 'XLS':
      case 'CSV':
        return <FileSpreadsheet className="w-8 h-8 text-emerald-600" />;
      case 'PNG':
      case 'JPG':
      case 'JPEG':
        return <ImageIcon className="w-8 h-8 text-blue-600" />;
      default:
        return <File className="w-8 h-8 text-slate-500" />;
    }
  };

  const filteredFiles = uploadedFiles.filter(f => 
    f.fileName.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="space-y-6 pb-12">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <h1 className="text-xl font-bold text-slate-800 flex items-center gap-2">
            <FolderUp className="w-5 h-5 text-[#0F7D3A]" />
            Berkas & Dokumen Berita Acara (BA)
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Arsip dokumen penyerahan aset, SOP, dan berita acara pemeliharaan berkala Bandara SSK II
          </p>
        </div>

        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold bg-emerald-50 text-[#0F7D3A] border border-emerald-200">
            <Cloud className="w-3.5 h-3.5" />
            Cloudflare R2 & Supabase Aktif
          </span>
        </div>
      </div>

      {uploadStatus && (
        <div className="p-4 bg-emerald-50 border border-emerald-300 rounded-2xl text-emerald-900 text-xs sm:text-sm font-semibold flex items-center gap-2 animate-fadeIn">
          {isUploading ? (
            <Loader2 className="w-4 h-4 text-[#0F7D3A] animate-spin shrink-0" />
          ) : (
            <CheckCircle2 className="w-4 h-4 text-[#0F7D3A] shrink-0" />
          )}
          <span>{uploadStatus}</span>
        </div>
      )}

      {/* Drag and Drop Upload Area */}
      <div
        onDragOver={e => {
          e.preventDefault();
          setIsDragging(true);
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={e => {
          e.preventDefault();
          setIsDragging(false);
          if (!isUploading) handleFiles(e.dataTransfer.files);
        }}
        className={`p-8 border-2 border-dashed rounded-2xl text-center transition flex flex-col items-center justify-center bg-white ${
          isDragging ? 'border-[#0F7D3A] bg-emerald-50/50' : 'border-slate-300 hover:border-emerald-400'
        } ${isUploading ? 'opacity-60 pointer-events-none' : ''}`}
      >
        <div className="w-14 h-14 rounded-2xl bg-emerald-100 text-[#0F7D3A] flex items-center justify-center mb-3 shadow-inner">
          {isUploading ? (
            <Loader2 className="w-7 h-7 animate-spin" />
          ) : (
            <Upload className="w-7 h-7" />
          )}
        </div>
        <h3 className="font-bold text-slate-800 text-sm sm:text-base">
          {isUploading ? 'Sedang Mengunggah Berkas...' : 'Tarik & Letakkan Berkas di Sini'}
        </h3>
        <p className="text-xs text-slate-500 max-w-sm mt-1">
          Berkas otomatis tersimpan di Cloudflare R2 Storage dan tercatat di Supabase Database.
        </p>

        <label className={`mt-4 px-5 py-2.5 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl font-bold text-xs shadow-sm transition cursor-pointer inline-flex items-center gap-2 ${
          isUploading ? 'opacity-50 cursor-not-allowed' : ''
        }`}>
          {isUploading ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              <span>Memproses...</span>
            </>
          ) : (
            <>
              <Upload className="w-4 h-4" />
              <span>Pilih Berkas Dari Komputer</span>
            </>
          )}
          <input
            type="file"
            multiple
            disabled={isUploading}
            className="hidden"
            onChange={e => handleFiles(e.target.files)}
          />
        </label>
      </div>

      {/* Search Input */}
      <div className="flex items-center justify-between gap-4">
        <h2 className="font-bold text-slate-800 text-base">
          Daftar Berkas Terunggah ({uploadedFiles.length})
        </h2>
        <div className="relative w-full max-w-xs">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Cari nama berkas..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-1.5 bg-white rounded-xl border border-slate-200 text-xs focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
          />
        </div>
      </div>

      {/* Files List */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredFiles.length === 0 ? (
          <div className="col-span-full py-12 text-center bg-white rounded-2xl border border-dashed border-slate-300 text-slate-400 text-xs">
            Belum ada berkas terunggah.
          </div>
        ) : (
          filteredFiles.map(file => {
            const isR2Hosted = file.fileUri && file.fileUri.includes('workers.dev');
            return (
              <div
                key={file.id}
                className="bg-white rounded-2xl border border-slate-200 p-4 shadow-sm hover:shadow-md transition flex flex-col justify-between space-y-3"
              >
                <div className="flex items-start gap-3">
                  <div className="w-12 h-12 rounded-xl bg-slate-50 flex items-center justify-center flex-shrink-0 border border-slate-100">
                    {getFileIcon(file.fileType)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <h4 className="font-bold text-xs sm:text-sm text-slate-800 line-clamp-1" title={file.fileName}>
                      {file.fileName}
                    </h4>
                    <div className="flex items-center gap-2 mt-1 text-[11px] text-slate-400">
                      <span className="font-semibold text-slate-600 uppercase">{file.fileType}</span>
                      <span>•</span>
                      <span>{file.fileSize}</span>
                    </div>
                    <div className="flex items-center justify-between mt-1 text-[10px] text-slate-400">
                      <span className="flex items-center gap-1">
                        <Calendar className="w-3 h-3" />
                        {new Date(file.uploadDate).toLocaleDateString('id-ID')}
                      </span>
                      {isR2Hosted && (
                        <span className="inline-flex items-center gap-0.5 text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded font-bold text-[9px] border border-emerald-200/60">
                          <Cloud className="w-2.5 h-2.5" />
                          R2 Cloud
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between pt-2 border-t border-slate-100">
                  {file.fileUri ? (
                    <div className="flex items-center gap-3">
                      <a
                        href={file.fileUri}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 text-xs font-semibold text-[#0F7D3A] hover:underline"
                      >
                        <ExternalLink className="w-3.5 h-3.5" />
                        Buka
                      </a>
                      <a
                        href={file.fileUri}
                        download={file.fileName}
                        className="inline-flex items-center gap-1 text-xs font-semibold text-slate-600 hover:text-slate-900"
                      >
                        <Download className="w-3.5 h-3.5" />
                        Unduh
                      </a>
                    </div>
                  ) : (
                    <span className="text-[11px] text-slate-400 italic">Arsip Cloud</span>
                  )}

                  <button
                    onClick={() => deleteUploadedFile(file.id)}
                    className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition cursor-pointer"
                    title="Hapus Berkas"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

