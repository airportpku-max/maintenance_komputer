import { supabase } from './supabaseClient';
import { Device, MaintenanceLog, TroubleTicket, UploadedFile } from '../types';

// ============================================================================
// DEVICE MAPPING & SYNC
// ============================================================================

export function mapSupabaseToDevice(row: any): Device {
  return {
    id: Number(row.id),
    type: (row.type as 'AIO' | 'Laptop') || 'Laptop',
    name: row.name || '',
    brand: row.brand || '',
    serialNumber: row.barcode_id || '',
    condition: (row.condition as 'Baik' | 'Trouble') || 'Baik',
    lastMaintenance: row.last_maintenance ? Number(row.last_maintenance) : 0,
    description: row.description || '',
    sn: row.sn || '',
    photoUri: row.photo_uri || null,
    baFileUri: row.ba_file_uri || null,
    baFileName: row.ba_file_name || null,
  };
}

export function mapDeviceToSupabase(device: Partial<Device>): any {
  const row: any = {};
  if (device.id !== undefined) row.id = device.id;
  if (device.type !== undefined) row.type = device.type;
  if (device.name !== undefined) row.name = device.name;
  if (device.brand !== undefined) row.brand = device.brand;
  if (device.serialNumber !== undefined) row.barcode_id = device.serialNumber;
  if (device.condition !== undefined) row.condition = device.condition;
  if (device.lastMaintenance !== undefined) row.last_maintenance = device.lastMaintenance;
  if (device.description !== undefined) row.description = device.description;
  if (device.sn !== undefined) row.sn = device.sn;
  if (device.photoUri !== undefined) row.photo_uri = device.photoUri;
  if (device.baFileUri !== undefined) row.ba_file_uri = device.baFileUri;
  if (device.baFileName !== undefined) row.ba_file_name = device.baFileName;
  return row;
}

export async function fetchDevicesFromSupabase(): Promise<Device[] | null> {
  try {
    const { data, error } = await supabase
      .from('devices')
      .select('*')
      .order('id', { ascending: true });

    if (error) {
      console.warn('Supabase fetch devices error:', error);
      return null;
    }
    return (data || []).map(mapSupabaseToDevice);
  } catch (err) {
    console.error('Error in fetchDevicesFromSupabase:', err);
    return null;
  }
}

export async function upsertDeviceToSupabase(device: Device): Promise<boolean> {
  try {
    const row = mapDeviceToSupabase(device);
    const { error } = await supabase.from('devices').upsert(row);
    if (error) {
      console.warn('Supabase upsert device error:', error);
      return false;
    }
    return true;
  } catch (err) {
    console.error('Error upserting device to Supabase:', err);
    return false;
  }
}

export async function deleteDeviceFromSupabase(id: number): Promise<boolean> {
  try {
    const { error } = await supabase.from('devices').delete().eq('id', id);
    if (error) {
      console.warn('Supabase delete device error:', error);
      return false;
    }
    return true;
  } catch (err) {
    console.error('Error deleting device from Supabase:', err);
    return false;
  }
}

// ============================================================================
// MAINTENANCE LOGS MAPPING & SYNC
// ============================================================================

export function mapSupabaseToLog(row: any): MaintenanceLog {
  // Extract technician name if encoded in action_taken or default
  let technicianName = 'Teknisi Bandara';
  let cleanAction = row.action_taken || 'Pemeliharaan Rutin';
  if (cleanAction.startsWith('[Teknisi:')) {
    const match = cleanAction.match(/^\[Teknisi:\s*(.*?)\]\s*(.*)$/);
    if (match) {
      technicianName = match[1];
      cleanAction = match[2];
    }
  }

  return {
    id: Number(row.id),
    deviceId: Number(row.device_id) || 0,
    deviceName: row.device_name || '',
    technicianName,
    actionTaken: cleanAction,
    timestamp: row.timestamp ? Number(row.timestamp) : Date.now(),
    healthReport: !!row.health_report,
    diskCleanup: !!row.disk_cleanup,
    hardwareCleanup: !!row.hardware_cleanup,
    checkingDriveError: !!row.checking_drive_error,
    scanningVirus: !!row.scanning_virus,
    checkingNetwork: !!row.checking_network,
    updatingAntivirus: !!row.updating_antivirus,
    updatingAplikasi: !!row.updating_aplikasi,
    notes: row.notes || null,
    signatureData: row.signature_data || null,
    techSignatureData: row.tech_signature_data || null,
    healthReportBeforePhoto: row.health_report_before_photo || null,
    healthReportAfterPhoto: row.health_report_after_photo || null,
    diskCleanupBeforePhoto: row.disk_cleanup_before_photo || null,
    diskCleanupAfterPhoto: row.disk_cleanup_after_photo || null,
    hardwareCleanupBeforePhoto: row.hardware_cleanup_before_photo || null,
    hardwareCleanupAfterPhoto: row.hardware_cleanup_after_photo || null,
    checkingDriveErrorBeforePhoto: row.checking_drive_error_before_photo || null,
    checkingDriveErrorAfterPhoto: row.checking_drive_error_after_photo || null,
    scanningVirusBeforePhoto: row.scanning_virus_before_photo || null,
    scanningVirusAfterPhoto: row.scanning_virus_after_photo || null,
    checkingNetworkBeforePhoto: row.checking_network_before_photo || null,
    checkingNetworkAfterPhoto: row.checking_network_after_photo || null,
    updatingAntivirusBeforePhoto: row.updating_antivirus_before_photo || null,
    updatingAntivirusAfterPhoto: row.updating_antivirus_after_photo || null,
    updatingAplikasiBeforePhoto: row.updating_aplikasi_before_photo || null,
    updatingAplikasiAfterPhoto: row.updating_aplikasi_after_photo || null,
  };
}

export function mapLogToSupabase(log: MaintenanceLog): any {
  const actionWithTech = log.technicianName 
    ? `[Teknisi: ${log.technicianName}] ${log.actionTaken || ''}`
    : (log.actionTaken || '');

  return {
    id: log.id,
    device_id: log.deviceId,
    device_name: log.deviceName,
    action_taken: actionWithTech,
    timestamp: log.timestamp,
    health_report: log.healthReport,
    disk_cleanup: log.diskCleanup,
    hardware_cleanup: log.hardwareCleanup,
    checking_drive_error: log.checkingDriveError,
    scanning_virus: log.scanningVirus,
    checking_network: log.checkingNetwork,
    updating_antivirus: log.updatingAntivirus,
    updating_aplikasi: log.updatingAplikasi,
    notes: log.notes || null,
    signature_data: log.signatureData || null,
    tech_signature_data: log.techSignatureData || null,
    health_report_before_photo: log.healthReportBeforePhoto || null,
    health_report_after_photo: log.healthReportAfterPhoto || null,
    disk_cleanup_before_photo: log.diskCleanupBeforePhoto || null,
    disk_cleanup_after_photo: log.diskCleanupAfterPhoto || null,
    hardware_cleanup_before_photo: log.hardwareCleanupBeforePhoto || null,
    hardware_cleanup_after_photo: log.hardwareCleanupAfterPhoto || null,
    checking_drive_error_before_photo: log.checkingDriveErrorBeforePhoto || null,
    checking_drive_error_after_photo: log.checkingDriveErrorAfterPhoto || null,
    scanning_virus_before_photo: log.scanningVirusBeforePhoto || null,
    scanning_virus_after_photo: log.scanningVirusAfterPhoto || null,
    checking_network_before_photo: log.checkingNetworkBeforePhoto || null,
    checking_network_after_photo: log.checkingNetworkAfterPhoto || null,
    updating_antivirus_before_photo: log.updatingAntivirusBeforePhoto || null,
    updating_antivirus_after_photo: log.updatingAntivirusAfterPhoto || null,
    updating_aplikasi_before_photo: log.updatingAplikasiBeforePhoto || null,
    updating_aplikasi_after_photo: log.updatingAplikasiAfterPhoto || null,
  };
}

export async function fetchMaintenanceLogsFromSupabase(): Promise<MaintenanceLog[] | null> {
  try {
    const { data, error } = await supabase
      .from('maintenance_logs')
      .select('*')
      .order('timestamp', { ascending: false });

    if (error) {
      console.warn('Supabase fetch logs error:', error);
      return null;
    }
    return (data || []).map(mapSupabaseToLog);
  } catch (err) {
    console.error('Error in fetchMaintenanceLogsFromSupabase:', err);
    return null;
  }
}

export async function insertLogToSupabase(log: MaintenanceLog): Promise<boolean> {
  try {
    const row = mapLogToSupabase(log);
    const { error } = await supabase.from('maintenance_logs').upsert(row);
    if (error) {
      console.warn('Supabase insert log error:', error);
      return false;
    }
    return true;
  } catch (err) {
    console.error('Error inserting log to Supabase:', err);
    return false;
  }
}

export async function deleteLogFromSupabase(id: number): Promise<boolean> {
  try {
    const { error } = await supabase.from('maintenance_logs').delete().eq('id', id);
    if (error) {
      console.warn('Supabase delete log error:', error);
      return false;
    }
    return true;
  } catch (err) {
    console.error('Error deleting log from Supabase:', err);
    return false;
  }
}

// ============================================================================
// TROUBLE TICKETS MAPPING & SYNC
// ============================================================================

export function mapSupabaseToTicket(row: any): TroubleTicket {
  let ts = Date.now();
  if (row.timestamp) {
    ts = typeof row.timestamp === 'string' ? new Date(row.timestamp).getTime() : Number(row.timestamp);
  }

  return {
    id: Number(row.id),
    deviceId: Number(row.device_id) || 0,
    deviceName: row.device_name || '',
    description: row.description || '',
    reportedBy: row.reported_by || '',
    status: (row.status as 'Pending' | 'Selesai') || 'Pending',
    timestamp: ts,
    actionTaken: row.action_taken || '',
    duration: row.duration || '',
    photoBefore: row.photo_before || null,
    photoAfter: row.photo_after || null,
  };
}

export function mapTicketToSupabase(ticket: TroubleTicket): any {
  return {
    id: ticket.id,
    device_id: ticket.deviceId,
    device_name: ticket.deviceName,
    description: ticket.description,
    reported_by: ticket.reportedBy,
    status: ticket.status,
    timestamp: new Date(ticket.timestamp).toISOString(),
    action_taken: ticket.actionTaken,
    duration: ticket.duration,
    photo_before: ticket.photoBefore || null,
    photo_after: ticket.photoAfter || null,
  };
}

export async function fetchTicketsFromSupabase(): Promise<TroubleTicket[] | null> {
  try {
    const { data, error } = await supabase
      .from('trouble_tickets')
      .select('*')
      .order('id', { ascending: false });

    if (error) {
      console.warn('Supabase fetch tickets error:', error);
      return null;
    }
    return (data || []).map(mapSupabaseToTicket);
  } catch (err) {
    console.error('Error in fetchTicketsFromSupabase:', err);
    return null;
  }
}

export async function upsertTicketToSupabase(ticket: TroubleTicket): Promise<boolean> {
  try {
    const row = mapTicketToSupabase(ticket);
    const { error } = await supabase.from('trouble_tickets').upsert(row);
    if (error) {
      console.warn('Supabase upsert ticket error:', error);
      return false;
    }
    return true;
  } catch (err) {
    console.error('Error upserting ticket to Supabase:', err);
    return false;
  }
}

export async function deleteTicketFromSupabase(id: number): Promise<boolean> {
  try {
    const { error } = await supabase.from('trouble_tickets').delete().eq('id', id);
    if (error) {
      console.warn('Supabase delete ticket error:', error);
      return false;
    }
    return true;
  } catch (err) {
    console.error('Error deleting ticket from Supabase:', err);
    return false;
  }
}

// ============================================================================
// UPLOADED FILES MAPPING & SYNC
// ============================================================================

export function mapSupabaseToFile(row: any): UploadedFile {
  let dateVal = Date.now();
  if (row.upload_date) {
    dateVal = typeof row.upload_date === 'string' ? new Date(row.upload_date).getTime() : Number(row.upload_date);
  }

  return {
    id: Number(row.id),
    fileName: row.file_name || 'Dokumen',
    fileSize: row.file_size || '0 KB',
    uploadDate: dateVal,
    fileType: row.file_type || 'FILE',
    fileUri: row.file_uri || null,
  };
}

export function mapFileToSupabase(file: UploadedFile): any {
  return {
    id: file.id,
    file_name: file.fileName,
    file_size: file.fileSize,
    upload_date: new Date(file.uploadDate).toISOString(),
    file_type: file.fileType,
    file_uri: file.fileUri || null,
  };
}

export async function fetchFilesFromSupabase(): Promise<UploadedFile[] | null> {
  try {
    const { data, error } = await supabase
      .from('uploaded_files')
      .select('*')
      .order('id', { ascending: false });

    if (error) {
      console.warn('Supabase fetch uploaded files error:', error);
      return null;
    }
    return (data || []).map(mapSupabaseToFile);
  } catch (err) {
    console.error('Error in fetchFilesFromSupabase:', err);
    return null;
  }
}

export async function insertFileToSupabase(file: UploadedFile): Promise<boolean> {
  try {
    const row = mapFileToSupabase(file);
    const { error } = await supabase.from('uploaded_files').upsert(row);
    if (error) {
      console.warn('Supabase insert file error:', error);
      return false;
    }
    return true;
  } catch (err) {
    console.error('Error inserting file to Supabase:', err);
    return false;
  }
}

export async function deleteFileFromSupabase(id: number): Promise<boolean> {
  try {
    const { error } = await supabase.from('uploaded_files').delete().eq('id', id);
    if (error) {
      console.warn('Supabase delete file error:', error);
      return false;
    }
    return true;
  } catch (err) {
    console.error('Error deleting file from Supabase:', err);
    return false;
  }
}
