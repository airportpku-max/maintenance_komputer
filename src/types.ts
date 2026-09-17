export interface Device {
  id: number;
  type: 'AIO' | 'Laptop';
  name: string; // Nama user
  brand: string; // Tipe & Merek perangkat
  serialNumber: string; // Barcode ID (e.g. BC-AIO-10293)
  condition: 'Baik' | 'Trouble';
  lastMaintenance: number;
  description: string; // Lokasi (e.g. Ruang Operasional, Finance, dsb)
  sn: string; // Serial Number hardware
  photoUri?: string | null; // Foto fisik perangkat
  baFileUri?: string | null; // File Berita Acara URI
  baFileName?: string | null; // Nama file BA
}

export interface Technician {
  id: number;
  name: string;
  role: string; // e.g. Hardware Specialist, Network Engineer, Software
  phone: string;
  status: 'Aktif' | 'Tidak Aktif';
  photoUri?: string | null;
}

export interface TroubleTicket {
  id: number;
  deviceId: number;
  deviceName: string;
  description: string;
  reportedBy: string;
  status: 'Pending' | 'Selesai';
  timestamp: number;
  actionTaken: string;
  duration: string;
  photoBefore?: string | null;
  photoAfter?: string | null;
}

export interface MaintenanceLog {
  id: number;
  deviceId: number;
  deviceName: string;
  technicianName: string;
  actionTaken: string;
  timestamp: number;
  healthReport: boolean;
  diskCleanup: boolean;
  hardwareCleanup: boolean;
  checkingDriveError: boolean;
  scanningVirus: boolean;
  checkingNetwork: boolean;
  updatingAntivirus: boolean;
  updatingAplikasi: boolean;
  windowsLicense?: string | null;
  officeLicense?: string | null;
  notes?: string | null;
  signatureData?: string | null; // User / Staf signature
  techSignatureData?: string | null; // Technician signature
  // Before / After Photos for each PM task
  healthReportBeforePhoto?: string | null;
  healthReportAfterPhoto?: string | null;
  diskCleanupBeforePhoto?: string | null;
  diskCleanupAfterPhoto?: string | null;
  hardwareCleanupBeforePhoto?: string | null;
  hardwareCleanupAfterPhoto?: string | null;
  checkingDriveErrorBeforePhoto?: string | null;
  checkingDriveErrorAfterPhoto?: string | null;
  scanningVirusBeforePhoto?: string | null;
  scanningVirusAfterPhoto?: string | null;
  checkingNetworkBeforePhoto?: string | null;
  checkingNetworkAfterPhoto?: string | null;
  updatingAntivirusBeforePhoto?: string | null;
  updatingAntivirusAfterPhoto?: string | null;
  updatingAplikasiBeforePhoto?: string | null;
  updatingAplikasiAfterPhoto?: string | null;
}

export interface UploadedFile {
  id: number;
  fileName: string;
  fileSize: string;
  uploadDate: number;
  fileType: 'PDF' | 'XLSX' | 'PNG' | 'DOCX' | string;
  fileUri?: string | null;
}

export type ScreenRoute = 
  | 'dashboard'
  | 'data_master'
  | 'upload_file'
  | 'trouble'
  | 'scanner'
  | 'profile'
  | 'report'
  | 'batch_maintenance';
