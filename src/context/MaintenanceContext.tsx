import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { Device, Technician, TroubleTicket, MaintenanceLog, UploadedFile } from '../types';
import { INITIAL_DEVICES, INITIAL_TECHNICIANS, INITIAL_TICKETS, INITIAL_LOGS, INITIAL_FILES } from '../data/initialData';
import {
  fetchDevicesFromSupabase,
  upsertDeviceToSupabase,
  deleteDeviceFromSupabase,
  fetchMaintenanceLogsFromSupabase,
  insertLogToSupabase,
  deleteLogFromSupabase,
  fetchTicketsFromSupabase,
  upsertTicketToSupabase,
  deleteTicketFromSupabase,
  fetchFilesFromSupabase,
  insertFileToSupabase,
  deleteFileFromSupabase,
} from '../services/dataSync';
import { deleteFileFromR2 } from '../services/r2Storage';

interface MaintenanceContextType {
  devices: Device[];
  technicians: Technician[];
  troubleTickets: TroubleTicket[];
  maintenanceLogs: MaintenanceLog[];
  uploadedFiles: UploadedFile[];
  
  // Cloud Sync Status
  isCloudConnected: boolean;
  isSyncing: boolean;
  lastSyncTime: Date | null;
  syncError: string | null;
  refreshFromCloud: () => Promise<void>;

  // Device actions
  addDevice: (device: Omit<Device, 'id'>) => Device;
  updateDevice: (device: Device) => void;
  deleteDevice: (id: number) => void;
  
  // Trouble Ticket actions
  addTroubleTicket: (ticket: Omit<TroubleTicket, 'id' | 'timestamp'>) => TroubleTicket;
  resolveTroubleTicket: (id: number, actionTaken: string, duration: string, photoAfter?: string | null, technicianName?: string) => void;
  deleteTroubleTicket: (id: number) => void;
  
  // Maintenance Log actions
  addMaintenanceLog: (log: Omit<MaintenanceLog, 'id' | 'timestamp'>) => MaintenanceLog;
  deleteMaintenanceLog: (id: number) => void;
  
  // Technician actions
  addTechnician: (tech: Omit<Technician, 'id'>) => Technician;
  updateTechnician: (tech: Technician) => void;
  deleteTechnician: (id: number) => void;
  
  // File actions
  addUploadedFile: (file: Omit<UploadedFile, 'id' | 'uploadDate'>) => UploadedFile;
  deleteUploadedFile: (id: number) => void;
  
  // Utility functions
  isDeviceAlreadyMaintainedInMonth: (device: Device, targetTimestamp?: number) => boolean;
  exportBackupJson: () => string;
  importBackupJson: (jsonData: string) => boolean;
  resetToInitialData: () => void;
}

const MaintenanceContext = createContext<MaintenanceContextType | undefined>(undefined);

export const MaintenanceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [devices, setDevices] = useState<Device[]>(() => {
    const saved = localStorage.getItem('cms_devices');
    if (!saved) return INITIAL_DEVICES;
    try {
      const parsed: Device[] = JSON.parse(saved);
      return parsed;
    } catch {
      return INITIAL_DEVICES;
    }
  });

  const [technicians, setTechnicians] = useState<Technician[]>(() => {
    const saved = localStorage.getItem('cms_technicians');
    return saved ? JSON.parse(saved) : INITIAL_TECHNICIANS;
  });

  const [troubleTickets, setTroubleTickets] = useState<TroubleTicket[]>(() => {
    const saved = localStorage.getItem('cms_tickets');
    return saved ? JSON.parse(saved) : INITIAL_TICKETS;
  });

  const [maintenanceLogs, setMaintenanceLogs] = useState<MaintenanceLog[]>(() => {
    const saved = localStorage.getItem('cms_logs');
    return saved ? JSON.parse(saved) : INITIAL_LOGS;
  });

  const [uploadedFiles, setUploadedFiles] = useState<UploadedFile[]>(() => {
    const saved = localStorage.getItem('cms_files');
    return saved ? JSON.parse(saved) : INITIAL_FILES;
  });

  const [isCloudConnected, setIsCloudConnected] = useState<boolean>(true);
  const [isSyncing, setIsSyncing] = useState<boolean>(false);
  const [lastSyncTime, setLastSyncTime] = useState<Date | null>(null);
  const [syncError, setSyncError] = useState<string | null>(null);

  // Sync to localStorage as offline cache
  useEffect(() => {
    localStorage.setItem('cms_devices', JSON.stringify(devices));
  }, [devices]);

  useEffect(() => {
    localStorage.setItem('cms_technicians', JSON.stringify(technicians));
  }, [technicians]);

  useEffect(() => {
    localStorage.setItem('cms_tickets', JSON.stringify(troubleTickets));
  }, [troubleTickets]);

  useEffect(() => {
    localStorage.setItem('cms_logs', JSON.stringify(maintenanceLogs));
  }, [maintenanceLogs]);

  useEffect(() => {
    localStorage.setItem('cms_files', JSON.stringify(uploadedFiles));
  }, [uploadedFiles]);

  // Initial fetch and cloud synchronization
  const refreshFromCloud = useCallback(async () => {
    setIsSyncing(true);
    setSyncError(null);
    try {
      const [cloudDevices, cloudLogs, cloudTickets, cloudFiles] = await Promise.all([
        fetchDevicesFromSupabase(),
        fetchMaintenanceLogsFromSupabase(),
        fetchTicketsFromSupabase(),
        fetchFilesFromSupabase(),
      ]);

      let connected = false;

      if (cloudDevices && cloudDevices.length > 0) {
        setDevices(cloudDevices);
        connected = true;
      }

      if (cloudLogs) {
        if (cloudLogs.length > 0) {
          setMaintenanceLogs(cloudLogs);
        }
        connected = true;
      }

      if (cloudTickets) {
        if (cloudTickets.length > 0) {
          setTroubleTickets(cloudTickets);
        }
        connected = true;
      }

      if (cloudFiles) {
        if (cloudFiles.length > 0) {
          setUploadedFiles(cloudFiles);
        }
        connected = true;
      }

      setIsCloudConnected(connected);
      setLastSyncTime(new Date());
    } catch (err: any) {
      console.warn('Cloud sync warning:', err);
      setSyncError(err?.message || 'Sinkronisasi cloud tertunda');
      setIsCloudConnected(false);
    } finally {
      setIsSyncing(false);
    }
  }, []);

  useEffect(() => {
    refreshFromCloud();
  }, [refreshFromCloud]);

  const isDeviceAlreadyMaintainedInMonth = (device: Device, targetTimestamp = Date.now()) => {
    const targetDate = new Date(targetTimestamp);
    const targetMonth = targetDate.getMonth();
    const targetYear = targetDate.getFullYear();

    return maintenanceLogs.some(log => {
      if (log.deviceId !== device.id) return false;
      const logDate = new Date(log.timestamp);
      return logDate.getMonth() === targetMonth && logDate.getFullYear() === targetYear;
    });
  };

  const addDevice = (newDevData: Omit<Device, 'id'>): Device => {
    const nextId = devices.length > 0 ? Math.max(...devices.map(d => d.id)) + 1 : 1;
    const newDevice: Device = {
      ...newDevData,
      id: nextId,
      lastMaintenance: newDevData.lastMaintenance || Date.now()
    };
    setDevices(prev => [newDevice, ...prev]);

    // Upsert to Supabase in background
    upsertDeviceToSupabase(newDevice).catch(err => console.warn('Supabase device add failed:', err));

    return newDevice;
  };

  const updateDevice = (updatedDevice: Device) => {
    setDevices(prev => prev.map(d => (d.id === updatedDevice.id ? updatedDevice : d)));
    
    // Upsert to Supabase in background
    upsertDeviceToSupabase(updatedDevice).catch(err => console.warn('Supabase device update failed:', err));
  };

  const deleteDevice = (id: number) => {
    setDevices(prev => prev.filter(d => d.id !== id));
    
    // Delete from Supabase in background
    deleteDeviceFromSupabase(id).catch(err => console.warn('Supabase device delete failed:', err));
  };

  const addTroubleTicket = (ticketData: Omit<TroubleTicket, 'id' | 'timestamp'>): TroubleTicket => {
    const nextId = troubleTickets.length > 0 ? Math.max(...troubleTickets.map(t => t.id)) + 1 : 1;
    const newTicket: TroubleTicket = {
      ...ticketData,
      id: nextId,
      timestamp: Date.now()
    };
    
    // Also update device condition to 'Trouble'
    if (newTicket.deviceId) {
      setDevices(prev => prev.map(d => {
        if (d.id === newTicket.deviceId) {
          const updated = { ...d, condition: 'Trouble' as const };
          upsertDeviceToSupabase(updated).catch(console.warn);
          return updated;
        }
        return d;
      }));
    }
    
    setTroubleTickets(prev => [newTicket, ...prev]);
    upsertTicketToSupabase(newTicket).catch(console.warn);
    return newTicket;
  };

  const resolveTroubleTicket = (
    id: number,
    actionTaken: string,
    duration: string,
    photoAfter?: string | null,
    technicianName?: string
  ) => {
    setTroubleTickets(prev => prev.map(ticket => {
      if (ticket.id === id) {
        // Also update corresponding device condition back to 'Baik'
        if (ticket.deviceId) {
          setDevices(prevDevs => prevDevs.map(d => {
            if (d.id === ticket.deviceId) {
              const updated = { ...d, condition: 'Baik' as const };
              upsertDeviceToSupabase(updated).catch(console.warn);
              return updated;
            }
            return d;
          }));
        }

        // Also create a maintenance log entry for tracking
        const nextLogId = maintenanceLogs.length > 0 ? Math.max(...maintenanceLogs.map(l => l.id)) + 1 : 1;
        const newLog: MaintenanceLog = {
          id: nextLogId,
          deviceId: ticket.deviceId,
          deviceName: ticket.deviceName,
          technicianName: technicianName || 'Teknisi Bandara',
          actionTaken: `[Penanganan Trouble]: ${actionTaken} (Durasi: ${duration})`,
          timestamp: Date.now(),
          healthReport: false,
          diskCleanup: false,
          hardwareCleanup: false,
          checkingDriveError: false,
          scanningVirus: false,
          checkingNetwork: false,
          updatingAntivirus: false,
          updatingAplikasi: false,
          notes: `Tiket Trouble diselesaikan: ${ticket.description}`
        };
        setMaintenanceLogs(prevLogs => [newLog, ...prevLogs]);
        insertLogToSupabase(newLog).catch(console.warn);

        const updatedTicket: TroubleTicket = {
          ...ticket,
          status: 'Selesai',
          actionTaken,
          duration,
          photoAfter: photoAfter || ticket.photoAfter
        };
        upsertTicketToSupabase(updatedTicket).catch(console.warn);
        return updatedTicket;
      }
      return ticket;
    }));
  };

  const deleteTroubleTicket = (id: number) => {
    setTroubleTickets(prev => prev.filter(t => t.id !== id));
    deleteTicketFromSupabase(id).catch(console.warn);
  };

  const addMaintenanceLog = (logData: Omit<MaintenanceLog, 'id' | 'timestamp'>): MaintenanceLog => {
    const nextId = maintenanceLogs.length > 0 ? Math.max(...maintenanceLogs.map(l => l.id)) + 1 : 1;
    const newLog: MaintenanceLog = {
      ...logData,
      id: nextId,
      timestamp: Date.now()
    };
    
    // Update device lastMaintenance & set condition to 'Baik'
    if (newLog.deviceId) {
      setDevices(prev => prev.map(d => {
        if (d.id === newLog.deviceId) {
          const updated = { ...d, lastMaintenance: Date.now(), condition: 'Baik' as const };
          upsertDeviceToSupabase(updated).catch(console.warn);
          return updated;
        }
        return d;
      }));
    }
    
    setMaintenanceLogs(prev => [newLog, ...prev]);
    insertLogToSupabase(newLog).catch(console.warn);
    return newLog;
  };

  const deleteMaintenanceLog = (id: number) => {
    setMaintenanceLogs(prev => prev.filter(l => l.id !== id));
    deleteLogFromSupabase(id).catch(console.warn);
  };

  const addTechnician = (techData: Omit<Technician, 'id'>): Technician => {
    const nextId = technicians.length > 0 ? Math.max(...technicians.map(t => t.id)) + 1 : 1;
    const newTech: Technician = {
      ...techData,
      id: nextId
    };
    setTechnicians(prev => [...prev, newTech]);
    return newTech;
  };

  const updateTechnician = (updatedTech: Technician) => {
    setTechnicians(prev => prev.map(t => t.id === updatedTech.id ? updatedTech : t));
  };

  const deleteTechnician = (id: number) => {
    setTechnicians(prev => prev.filter(t => t.id !== id));
  };

  const addUploadedFile = (fileData: Omit<UploadedFile, 'id' | 'uploadDate'>): UploadedFile => {
    const nextId = uploadedFiles.length > 0 ? Math.max(...uploadedFiles.map(f => f.id)) + 1 : 1;
    const newFile: UploadedFile = {
      ...fileData,
      id: nextId,
      uploadDate: Date.now()
    };
    setUploadedFiles(prev => [newFile, ...prev]);
    insertFileToSupabase(newFile).catch(console.warn);
    return newFile;
  };

  const deleteUploadedFile = (id: number) => {
    const fileToDelete = uploadedFiles.find(f => f.id === id);
    if (fileToDelete?.fileUri) {
      deleteFileFromR2(fileToDelete.fileUri).catch(console.warn);
    }
    setUploadedFiles(prev => prev.filter(f => f.id !== id));
    deleteFileFromSupabase(id).catch(console.warn);
  };

  const exportBackupJson = (): string => {
    const payload = {
      version: 1,
      appName: 'CMS Bandara Sultan Syarif Kasim II',
      exportDate: new Date().toISOString(),
      devices,
      technicians,
      troubleTickets,
      maintenanceLogs,
      uploadedFiles
    };
    return JSON.stringify(payload, null, 2);
  };

  const importBackupJson = (jsonData: string): boolean => {
    try {
      const parsed = JSON.parse(jsonData);
      if (Array.isArray(parsed.devices)) setDevices(parsed.devices);
      if (Array.isArray(parsed.technicians)) setTechnicians(parsed.technicians);
      if (Array.isArray(parsed.troubleTickets)) setTroubleTickets(parsed.troubleTickets);
      if (Array.isArray(parsed.maintenanceLogs)) setMaintenanceLogs(parsed.maintenanceLogs);
      if (Array.isArray(parsed.uploadedFiles)) setUploadedFiles(parsed.uploadedFiles);
      return true;
    } catch (e) {
      console.error('Failed to import backup:', e);
      return false;
    }
  };

  const resetToInitialData = () => {
    setDevices(INITIAL_DEVICES);
    setTechnicians(INITIAL_TECHNICIANS);
    setTroubleTickets(INITIAL_TICKETS);
    setMaintenanceLogs(INITIAL_LOGS);
    setUploadedFiles(INITIAL_FILES);
  };

  return (
    <MaintenanceContext.Provider
      value={{
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
        addDevice,
        updateDevice,
        deleteDevice,
        addTroubleTicket,
        resolveTroubleTicket,
        deleteTroubleTicket,
        addMaintenanceLog,
        deleteMaintenanceLog,
        addTechnician,
        updateTechnician,
        deleteTechnician,
        addUploadedFile,
        deleteUploadedFile,
        isDeviceAlreadyMaintainedInMonth,
        exportBackupJson,
        importBackupJson,
        resetToInitialData
      }}
    >
      {children}
    </MaintenanceContext.Provider>
  );
};

export const useMaintenance = () => {
  const context = useContext(MaintenanceContext);
  if (!context) {
    throw new Error('useMaintenance must be used within a MaintenanceProvider');
  }
  return context;
};
