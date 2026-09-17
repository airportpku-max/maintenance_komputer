import React, { useState } from 'react';
import { MaintenanceProvider } from './context/MaintenanceContext';
import { Navbar } from './components/Navbar';
import { DashboardScreen } from './screens/DashboardScreen';
import { DataMasterScreen } from './screens/DataMasterScreen';
import { InputMaintenanceScreen } from './screens/InputMaintenanceScreen';
import { TroubleScreen } from './screens/TroubleScreen';
import { ReportScreen } from './screens/ReportScreen';
import { BarcodeScannerScreen } from './screens/BarcodeScannerScreen';
import { UploadFileScreen } from './screens/UploadFileScreen';
import { ProfileScreen } from './screens/ProfileScreen';
import { ScreenRoute } from './types';

export const App: React.FC = () => {
  const [currentRoute, setCurrentRoute] = useState<ScreenRoute>('dashboard');
  const [selectedDeviceIdForMaintenance, setSelectedDeviceIdForMaintenance] = useState<number | null>(null);

  const handleSelectDeviceForMaintenance = (deviceId: number) => {
    setSelectedDeviceIdForMaintenance(deviceId);
    setCurrentRoute('batch_maintenance');
  };

  return (
    <MaintenanceProvider>
      <div className="min-h-screen bg-slate-100/70 text-slate-900 flex flex-col font-sans selection:bg-emerald-500 selection:text-white">
        <Navbar 
          currentRoute={currentRoute} 
          onNavigate={(route) => {
            setCurrentRoute(route);
            window.scrollTo({ top: 0, behavior: 'smooth' });
          }} 
        />

        <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 pt-6">
          {currentRoute === 'dashboard' && (
            <DashboardScreen 
              onNavigate={setCurrentRoute} 
            />
          )}

          {currentRoute === 'data_master' && (
            <DataMasterScreen 
              onNavigate={setCurrentRoute}
              onSelectDeviceForMaintenance={handleSelectDeviceForMaintenance}
            />
          )}

          {currentRoute === 'batch_maintenance' && (
            <InputMaintenanceScreen 
              onNavigate={setCurrentRoute}
              preselectedDeviceId={selectedDeviceIdForMaintenance}
            />
          )}

          {currentRoute === 'trouble' && (
            <TroubleScreen 
              onNavigate={setCurrentRoute}
            />
          )}

          {currentRoute === 'report' && (
            <ReportScreen />
          )}

          {currentRoute === 'scanner' && (
            <BarcodeScannerScreen 
              onNavigate={setCurrentRoute}
              onSelectDeviceForMaintenance={handleSelectDeviceForMaintenance}
            />
          )}

          {currentRoute === 'upload_file' && (
            <UploadFileScreen />
          )}

          {currentRoute === 'profile' && (
            <ProfileScreen />
          )}
        </main>

        <footer className="bg-white border-t border-slate-200 py-6 text-center text-xs text-slate-500 no-print mt-auto">
          <div className="max-w-7xl mx-auto px-4 space-y-1">
            <p className="font-semibold text-slate-700">
              CMS (Computer Maintenance System) — Bandara Internasional Sultan Syarif Kasim II Pekanbaru
            </p>
            <p className="text-slate-400">
              Unit Airport Technology & Sistem Informasi © {new Date().getFullYear()} • Versi Web Terintegrasi
            </p>
          </div>
        </footer>
      </div>
    </MaintenanceProvider>
  );
};

export default App;
