import React from 'react';
import { 
  Home, 
  Database, 
  CheckSquare, 
  AlertTriangle, 
  FileText, 
  QrCode, 
  FolderUp, 
  Settings, 
  Bell,
  Plane
} from 'lucide-react';
import { ScreenRoute } from '../types';
import { useMaintenance } from '../context/MaintenanceContext';

interface NavbarProps {
  currentRoute: ScreenRoute;
  onNavigate: (route: ScreenRoute) => void;
}

export const Navbar: React.FC<NavbarProps> = ({ currentRoute, onNavigate }) => {
  const { troubleTickets } = useMaintenance();
  const pendingTickets = troubleTickets.filter(t => t.status === 'Pending').length;

  const navItems: { id: ScreenRoute; label: string; icon: React.ReactNode; badge?: number }[] = [
    { id: 'dashboard', label: 'Dashboard', icon: <Home className="w-4 h-4" /> },
    { id: 'data_master', label: 'Data Master', icon: <Database className="w-4 h-4" /> },
    { id: 'batch_maintenance', label: 'Input Maintenance', icon: <CheckSquare className="w-4 h-4" /> },
    { id: 'trouble', label: 'Trouble Ticket', icon: <AlertTriangle className="w-4 h-4" />, badge: pendingTickets },
    { id: 'report', label: 'Laporan PDF', icon: <FileText className="w-4 h-4" /> },
    { id: 'scanner', label: 'Barcode Scanner', icon: <QrCode className="w-4 h-4" /> },
    { id: 'upload_file', label: 'Berkas', icon: <FolderUp className="w-4 h-4" /> },
    { id: 'profile', label: 'Profil & Sistem', icon: <Settings className="w-4 h-4" /> },
  ];

  return (
    <header className="sticky top-0 z-40 bg-gradient-to-r from-[#0A5527] to-[#0F7D3A] text-white shadow-md no-print">
      {/* Top Header Bar */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo & Airport identity */}
          <div className="flex items-center gap-3 cursor-pointer" onClick={() => onNavigate('dashboard')}>
            <div className="w-10 h-10 rounded-xl bg-white/10 p-1 flex items-center justify-center backdrop-blur-sm border border-white/20 overflow-hidden shadow-inner">
              <img 
                src="/logo.jpg" 
                alt="CMS Logo" 
                className="w-full h-full object-contain rounded-lg"
                onError={(e) => {
                  // Fallback icon if image fails
                  (e.target as HTMLElement).style.display = 'none';
                }}
              />
              <Plane className="w-5 h-5 text-emerald-300 hidden" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold text-lg tracking-tight text-white">CMS BANDARA</span>
                <span className="text-[10px] bg-emerald-800/80 text-emerald-200 px-2 py-0.5 rounded-full font-semibold border border-emerald-600/50 uppercase">
                  SSK II Pekanbaru
                </span>
              </div>
              <p className="text-[11px] text-emerald-100/80 font-normal">
                Computer Maintenance & Monitoring System
              </p>
            </div>
          </div>

          {/* Right Action Icons */}
          <div className="flex items-center gap-2 sm:gap-3">
            <button
              onClick={() => onNavigate('trouble')}
              className="relative p-2 rounded-xl bg-white/10 hover:bg-white/20 transition-all border border-white/10 text-white cursor-pointer"
              title="Tiket Trouble Pending"
            >
              <Bell className={`w-5 h-5 ${pendingTickets > 0 ? 'animate-bounce text-amber-300' : 'text-white'}`} />
              {pendingTickets > 0 && (
                <span className="absolute -top-1 -right-1 bg-red-500 text-white text-[10px] font-bold rounded-full w-5 h-5 flex items-center justify-center shadow-lg border-2 border-[#0A5527]">
                  {pendingTickets}
                </span>
              )}
            </button>

            <button
              onClick={() => onNavigate('scanner')}
              className="hidden sm:flex items-center gap-2 bg-emerald-500 hover:bg-emerald-400 text-slate-900 font-semibold text-xs px-3.5 py-2 rounded-xl transition shadow-md cursor-pointer"
            >
              <QrCode className="w-4 h-4 text-slate-950" />
              Scan Barcode
            </button>
          </div>
        </div>
      </div>

      {/* Navigation Tabs Bar */}
      <nav className="bg-[#08451F]/90 border-t border-emerald-800/60 overflow-x-auto scrollbar-none">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex space-x-1 sm:space-x-2 py-2 min-w-max">
            {navItems.map(item => {
              const isActive = currentRoute === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => onNavigate(item.id)}
                  className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition whitespace-nowrap cursor-pointer relative ${
                    isActive 
                      ? 'bg-white text-[#0A5527] shadow-sm font-semibold' 
                      : 'text-emerald-100/90 hover:bg-white/10 hover:text-white'
                  }`}
                >
                  {item.icon}
                  <span>{item.label}</span>
                  {item.badge !== undefined && item.badge > 0 && (
                    <span className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
                      isActive ? 'bg-red-500 text-white' : 'bg-red-500 text-white'
                    }`}>
                      {item.badge}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>
      </nav>
    </header>
  );
};
