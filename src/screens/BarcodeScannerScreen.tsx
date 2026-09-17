import React, { useState, useRef, useEffect } from 'react';
import { 
  QrCode, 
  Camera, 
  Search, 
  CheckCircle2, 
  AlertTriangle, 
  Monitor, 
  Laptop, 
  Wrench, 
  Eye, 
  ArrowRight,
  RefreshCw,
  Sparkles
} from 'lucide-react';
import { Device, ScreenRoute } from '../types';
import { useMaintenance } from '../context/MaintenanceContext';

interface BarcodeScannerScreenProps {
  onNavigate: (route: ScreenRoute) => void;
  onSelectDeviceForMaintenance?: (deviceId: number) => void;
}

export const BarcodeScannerScreen: React.FC<BarcodeScannerScreenProps> = ({
  onNavigate,
  onSelectDeviceForMaintenance
}) => {
  const { devices } = useMaintenance();
  const [manualCode, setManualCode] = useState('');
  const [matchedDevice, setMatchedDevice] = useState<Device | null>(null);
  const [isCameraActive, setIsCameraActive] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const startCamera = async () => {
    setCameraError(null);
    try {
      if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment' }
        });
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play();
        }
        setIsCameraActive(true);
      } else {
        setCameraError('Kamera tidak didukung pada browser ini atau berada di dalam iframe.');
      }
    } catch (err: any) {
      console.warn('Camera access error:', err);
      setCameraError('Izin akses kamera ditolak atau kamera tidak terdeteksi. Silakan gunakan input barcode manual di bawah.');
    }
  };

  const stopCamera = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }
    setIsCameraActive(false);
  };

  useEffect(() => {
    return () => {
      stopCamera();
    };
  }, []);

  const handleLookup = (code: string) => {
    const cleanCode = code.trim().toLowerCase();
    const found = devices.find(d => 
      d.serialNumber.toLowerCase() === cleanCode || 
      (d.sn && d.sn.toLowerCase() === cleanCode)
    );
    if (found) {
      setMatchedDevice(found);
    } else {
      setMatchedDevice(null);
      alert(`Perangkat dengan kode barcode "${code}" tidak ditemukan dalam sistem.`);
    }
  };

  return (
    <div className="max-w-2xl mx-auto space-y-6 pb-16">
      {/* Top Header */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm text-center">
        <div className="w-12 h-12 rounded-2xl bg-emerald-100 text-[#0F7D3A] flex items-center justify-center mx-auto mb-3">
          <QrCode className="w-6 h-6" />
        </div>
        <h1 className="text-xl font-bold text-slate-800">
          Barcode & QR Code Scanner
        </h1>
        <p className="text-xs text-slate-500 max-w-md mx-auto mt-1">
          Pindai stiker barcode fisik aset pada PC AIO atau Laptop bandara untuk pencatatan dan monitoring kilat
        </p>
      </div>

      {/* Camera Stream Viewport */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm space-y-4">
        <div className="relative aspect-video max-h-72 bg-slate-900 rounded-xl overflow-hidden flex flex-col items-center justify-center text-white">
          {isCameraActive ? (
            <video
              ref={videoRef}
              className="w-full h-full object-cover"
              autoPlay
              playsInline
              muted
            />
          ) : (
            <div className="p-6 text-center space-y-2">
              <Camera className="w-12 h-12 mx-auto text-slate-500" />
              <p className="text-sm font-semibold text-slate-300">Kamera Scanner Siap</p>
              <p className="text-xs text-slate-400 max-w-xs">
                Arahkan kamera ke stiker barcode inventaris perangkat.
              </p>
              <button
                type="button"
                onClick={startCamera}
                className="mt-2 inline-flex items-center gap-2 px-4 py-2 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl text-xs font-bold transition cursor-pointer"
              >
                <Camera className="w-4 h-4" />
                Aktifkan Kamera
              </button>
            </div>
          )}

          {isCameraActive && (
            <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
              {/* Scan target reticle box */}
              <div className="w-64 h-32 border-2 border-emerald-400/80 rounded-lg relative shadow-[0_0_0_9999px_rgba(0,0,0,0.4)]">
                <div className="absolute top-0 left-0 w-4 h-4 border-t-4 border-l-4 border-emerald-400 -mt-1 -ml-1" />
                <div className="absolute top-0 right-0 w-4 h-4 border-t-4 border-r-4 border-emerald-400 -mt-1 -mr-1" />
                <div className="absolute bottom-0 left-0 w-4 h-4 border-b-4 border-l-4 border-emerald-400 -mb-1 -ml-1" />
                <div className="absolute bottom-0 right-0 w-4 h-4 border-b-4 border-r-4 border-emerald-400 -mb-1 -mr-1" />
                <div className="w-full h-0.5 bg-red-500/80 animate-pulse absolute top-1/2 -translate-y-1/2 shadow-lg" />
              </div>
            </div>
          )}

          {isCameraActive && (
            <button
              onClick={stopCamera}
              className="absolute top-3 right-3 px-3 py-1 bg-black/60 hover:bg-black/80 text-white rounded-lg text-xs font-semibold backdrop-blur-sm cursor-pointer"
            >
              Matikan Kamera
            </button>
          )}
        </div>

        {cameraError && (
          <div className="p-3 bg-amber-50 border border-amber-200 text-amber-800 rounded-xl text-xs flex items-start gap-2">
            <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0 mt-0.5" />
            <span>{cameraError}</span>
          </div>
        )}

        {/* Manual Barcode Input */}
        <div className="pt-2">
          <label className="block text-xs font-semibold text-slate-700 mb-1">
            Atau Ketik / Tempel ID Barcode Manual:
          </label>
          <div className="flex gap-2">
            <input
              type="text"
              placeholder="Contoh: BC-AIO-10291 atau BC-LAP-40192"
              value={manualCode}
              onChange={e => setManualCode(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter') handleLookup(manualCode);
              }}
              className="flex-1 px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs sm:text-sm font-mono focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
            />
            <button
              type="button"
              onClick={() => handleLookup(manualCode)}
              className="px-4 py-2 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl text-xs font-bold transition shadow-sm cursor-pointer"
            >
              Cari Perangkat
            </button>
          </div>
        </div>

        {/* Sample Barcodes for Quick Testing */}
        <div className="pt-2 border-t border-slate-100">
          <span className="text-[11px] font-semibold text-slate-500 block mb-2">
            Pilih Barcode Cepat Untuk Testing:
          </span>
          <div className="flex flex-wrap gap-2">
            {devices.slice(0, 4).map(d => (
              <button
                key={d.id}
                type="button"
                onClick={() => {
                  setManualCode(d.serialNumber);
                  handleLookup(d.serialNumber);
                }}
                className="inline-flex items-center gap-1 px-2.5 py-1 bg-slate-100 hover:bg-emerald-100 text-slate-700 hover:text-emerald-900 rounded-lg text-xs font-mono font-medium transition cursor-pointer"
              >
                <Sparkles className="w-3 h-3 text-emerald-600" />
                {d.serialNumber} ({d.name})
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Matched Device Result Card */}
      {matchedDevice && (
        <div className="bg-white p-6 rounded-2xl border-2 border-emerald-400 shadow-lg space-y-4 animate-fade-in">
          <div className="flex items-start justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 rounded-xl bg-emerald-100 text-[#0F7D3A] flex items-center justify-center">
                {matchedDevice.type === 'AIO' ? <Monitor className="w-6 h-6" /> : <Laptop className="w-6 h-6" />}
              </div>
              <div>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 uppercase">
                  Perangkat Teridentifikasi
                </span>
                <h3 className="text-base sm:text-lg font-bold text-slate-900 mt-1">
                  {matchedDevice.name}
                </h3>
                <p className="text-xs font-semibold text-emerald-800">
                  {matchedDevice.brand}
                </p>
              </div>
            </div>

            <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${
              matchedDevice.condition === 'Baik' ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'
            }`}>
              {matchedDevice.condition}
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2 text-xs bg-slate-50 p-3 rounded-xl border border-slate-100">
            <div>
              <span className="text-slate-400 block text-[10px] uppercase">ID Barcode</span>
              <span className="font-mono font-bold text-slate-800">{matchedDevice.serialNumber}</span>
            </div>
            <div>
              <span className="text-slate-400 block text-[10px] uppercase">Serial Number (SN)</span>
              <span className="font-mono font-semibold text-slate-700">{matchedDevice.sn || '-'}</span>
            </div>
            <div className="col-span-2 pt-1 border-t border-slate-200/60">
              <span className="text-slate-400 block text-[10px] uppercase">Lokasi / Unit</span>
              <span className="text-slate-700 font-medium">{matchedDevice.description || '-'}</span>
            </div>
          </div>

          {/* Direct Action Buttons for Scanned Device */}
          <div className="grid grid-cols-2 gap-3 pt-2">
            <button
              onClick={() => {
                if (onSelectDeviceForMaintenance) {
                  onSelectDeviceForMaintenance(matchedDevice.id);
                }
                onNavigate('batch_maintenance');
              }}
              className="flex items-center justify-center gap-2 py-2.5 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl font-bold text-xs shadow-sm transition cursor-pointer"
            >
              <Wrench className="w-4 h-4" />
              Lakukan Maintenance
            </button>

            <button
              onClick={() => onNavigate('trouble')}
              className="flex items-center justify-center gap-2 py-2.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl font-bold text-xs shadow-sm transition cursor-pointer"
            >
              <AlertTriangle className="w-4 h-4" />
              Lapor Kerusakan
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
