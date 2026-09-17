import React, { useState } from 'react';
import { 
  AlertTriangle, 
  Plus, 
  Search, 
  CheckCircle2, 
  Clock, 
  Trash2, 
  Wrench, 
  Camera, 
  X, 
  Check, 
  User, 
  Monitor, 
  Laptop,
  ArrowRight,
  ShieldCheck
} from 'lucide-react';
import { Device, TroubleTicket, ScreenRoute } from '../types';
import { useMaintenance } from '../context/MaintenanceContext';
import { uploadFileToR2 } from '../services/r2Storage';

interface TroubleScreenProps {
  onNavigate: (route: ScreenRoute) => void;
}

export const TroubleScreen: React.FC<TroubleScreenProps> = ({ onNavigate }) => {
  const { 
    troubleTickets, 
    devices, 
    technicians, 
    addTroubleTicket, 
    resolveTroubleTicket, 
    deleteTroubleTicket 
  } = useMaintenance();

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedStatusTab, setSelectedStatusTab] = useState<'Semua' | 'Pending' | 'Selesai'>('Semua');

  // Modals
  const [showAddModal, setShowAddModal] = useState(false);
  const [showResolveModal, setShowResolveModal] = useState(false);
  const [selectedTicket, setSelectedTicket] = useState<TroubleTicket | null>(null);

  // New ticket form
  const [newDeviceId, setNewDeviceId] = useState<number>(devices[0]?.id || 0);
  const [description, setDescription] = useState('');
  const [reportedBy, setReportedBy] = useState('');
  const [photoBefore, setPhotoBefore] = useState<string | null>(null);

  // Resolve form
  const [technicianName, setTechnicianName] = useState<string>(technicians[0]?.name || 'Teknisi Bandara');
  const [actionTaken, setActionTaken] = useState('');
  const [duration, setDuration] = useState('30 Menit');
  const [photoAfter, setPhotoAfter] = useState<string | null>(null);

  const openAddModal = () => {
    setDescription('');
    setReportedBy('');
    setPhotoBefore(null);
    if (devices.length > 0) setNewDeviceId(devices[0].id);
    setShowAddModal(true);
  };

  const openResolveModal = (ticket: TroubleTicket) => {
    setSelectedTicket(ticket);
    setActionTaken('');
    setDuration('30 Menit');
    setPhotoAfter(null);
    if (technicians.length > 0) setTechnicianName(technicians[0].name);
    setShowResolveModal(true);
  };

  const handleSaveNewTicket = (e: React.FormEvent) => {
    e.preventDefault();
    const dev = devices.find(d => d.id === newDeviceId);
    if (!dev) {
      alert('Pilih perangkat terlebih dahulu!');
      return;
    }
    if (!description.trim()) {
      alert('Deskripsi kerusakan wajib diisi!');
      return;
    }

    addTroubleTicket({
      deviceId: dev.id,
      deviceName: `${dev.name} (${dev.brand})`,
      description,
      reportedBy: reportedBy || 'Staff Bandara',
      status: 'Pending',
      actionTaken: '',
      duration: '',
      photoBefore: photoBefore || null,
      photoAfter: null
    });

    setShowAddModal(false);
  };

  const handleResolveSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTicket) return;
    if (!actionTaken.trim()) {
      alert('Tindakan perbaikan wajib diisi!');
      return;
    }

    resolveTroubleTicket(
      selectedTicket.id,
      actionTaken,
      duration,
      photoAfter,
      technicianName
    );

    setShowResolveModal(false);
    setSelectedTicket(null);
  };

  const handleDelete = (id: number) => {
    if (window.confirm('Hapus tiket trouble ini?')) {
      deleteTroubleTicket(id);
    }
  };

  const filteredTickets = troubleTickets.filter(ticket => {
    const matchesSearch = 
      ticket.deviceName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      ticket.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      ticket.reportedBy.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesStatus = 
      selectedStatusTab === 'Semua' || 
      ticket.status === selectedStatusTab;

    return matchesSearch && matchesStatus;
  });

  const pendingCount = troubleTickets.filter(t => t.status === 'Pending').length;
  const completedCount = troubleTickets.filter(t => t.status === 'Selesai').length;

  return (
    <div className="space-y-6 pb-12">
      {/* Top Header Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <h1 className="text-xl font-bold text-slate-800 flex items-center gap-2">
            <AlertTriangle className="w-5 h-5 text-amber-500" />
            Trouble Ticket & Kerusakan (Corrective Maintenance)
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Pencatatan laporan kendala perangkat, penugasan teknisi dan bukti penyelesaian gangguan
          </p>
        </div>

        <button
          onClick={openAddModal}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl font-bold text-xs sm:text-sm shadow-sm transition cursor-pointer self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          Buat Tiket Trouble
        </button>
      </div>

      {/* Tabs & Search Filter */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        <div className="inline-flex p-1 bg-slate-200/70 rounded-xl">
          {(['Semua', 'Pending', 'Selesai'] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setSelectedStatusTab(tab)}
              className={`px-4 py-1.5 rounded-lg text-xs font-semibold transition cursor-pointer ${
                selectedStatusTab === tab
                  ? 'bg-white text-slate-900 shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {tab === 'Semua' ? `Semua (${troubleTickets.length})` : tab === 'Pending' ? `Pending (${pendingCount})` : `Selesai (${completedCount})`}
            </button>
          ))}
        </div>

        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Cari perangkat, pelapor, atau kendala..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-white rounded-xl border border-slate-200 text-xs sm:text-sm focus:outline-none focus:ring-2 focus:ring-[#0F7D3A] transition"
          />
        </div>
      </div>

      {/* Tickets List */}
      <div className="space-y-4">
        {filteredTickets.length === 0 ? (
          <div className="py-16 text-center bg-white rounded-2xl border border-dashed border-slate-300">
            <CheckCircle2 className="w-12 h-12 mx-auto text-emerald-400 mb-3" />
            <p className="text-slate-700 font-bold text-sm">Tidak ada tiket trouble yang cocok</p>
            <p className="text-slate-400 text-xs mt-1">Sistem operasional dalam keadaan aman dan lancar.</p>
          </div>
        ) : (
          filteredTickets.map(ticket => {
            const isPending = ticket.status === 'Pending';
            return (
              <div
                key={ticket.id}
                className={`p-5 rounded-2xl border transition bg-white shadow-sm hover:shadow-md flex flex-col md:flex-row md:items-center justify-between gap-4 ${
                  isPending ? 'border-amber-200 bg-amber-50/10' : 'border-slate-200'
                }`}
              >
                <div className="space-y-2 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className={`text-[10px] font-bold px-2.5 py-0.5 rounded-full flex items-center gap-1 ${
                      isPending ? 'bg-amber-100 text-amber-800' : 'bg-emerald-100 text-emerald-800'
                    }`}>
                      {isPending ? <Clock className="w-3 h-3" /> : <CheckCircle2 className="w-3 h-3" />}
                      {ticket.status}
                    </span>
                    <span className="font-bold text-slate-900 text-sm sm:text-base">
                      {ticket.deviceName}
                    </span>
                  </div>

                  <p className="text-xs sm:text-sm text-slate-700 font-medium">
                    {ticket.description}
                  </p>

                  <div className="flex flex-wrap items-center gap-4 text-xs text-slate-500 pt-1">
                    <span>Pelapor: <strong className="text-slate-700">{ticket.reportedBy}</strong></span>
                    <span>•</span>
                    <span>Waktu Lapor: {new Date(ticket.timestamp).toLocaleString('id-ID')}</span>
                  </div>

                  {/* Resolution Details if Selesai */}
                  {!isPending && ticket.actionTaken && (
                    <div className="p-3 bg-emerald-50/70 border border-emerald-200/70 rounded-xl text-xs space-y-1">
                      <div className="flex items-center gap-1 text-emerald-800 font-bold">
                        <ShieldCheck className="w-3.5 h-3.5" />
                        <span>Solusi Tindakan (Durasi: {ticket.duration || 'Selesai'}):</span>
                      </div>
                      <p className="text-emerald-950">{ticket.actionTaken}</p>
                    </div>
                  )}

                  {/* Photos Row */}
                  {(ticket.photoBefore || ticket.photoAfter) && (
                    <div className="flex items-center gap-3 pt-2">
                      {ticket.photoBefore && (
                        <div>
                          <span className="text-[10px] text-slate-400 block mb-0.5">Foto Before:</span>
                          <img 
                            src={ticket.photoBefore} 
                            alt="Before" 
                            className="w-16 h-16 object-cover rounded-lg border border-slate-200"
                          />
                        </div>
                      )}
                      {ticket.photoAfter && (
                        <div>
                          <span className="text-[10px] text-slate-400 block mb-0.5">Foto After:</span>
                          <img 
                            src={ticket.photoAfter} 
                            alt="After" 
                            className="w-16 h-16 object-cover rounded-lg border border-slate-200"
                          />
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* Right Action Buttons */}
                <div className="flex items-center gap-2 self-end md:self-center">
                  {isPending ? (
                    <button
                      onClick={() => openResolveModal(ticket)}
                      className="inline-flex items-center gap-1.5 px-4 py-2 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl text-xs font-bold transition shadow-sm cursor-pointer"
                    >
                      <Wrench className="w-3.5 h-3.5" />
                      Selesaikan Trouble
                    </button>
                  ) : (
                    <span className="text-xs font-semibold text-emerald-700 bg-emerald-50 px-3 py-1.5 rounded-xl border border-emerald-200 flex items-center gap-1">
                      <Check className="w-3.5 h-3.5" /> Telah Selesai
                    </span>
                  )}

                  <button
                    onClick={() => handleDelete(ticket.id)}
                    className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-xl transition cursor-pointer"
                    title="Hapus Tiket"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Add Trouble Ticket Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl space-y-4 my-8">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-bold text-lg text-slate-800 flex items-center gap-2">
                <AlertTriangle className="w-5 h-5 text-rose-600" />
                Buat Laporan Gangguan Baru
              </h3>
              <button onClick={() => setShowAddModal(false)} className="text-slate-400 hover:text-slate-600 cursor-pointer">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveNewTicket} className="space-y-4 text-xs sm:text-sm">
              <div>
                <label className="block font-semibold text-slate-700 mb-1">Pilih Perangkat Bermasalah *</label>
                <select
                  value={newDeviceId}
                  onChange={e => setNewDeviceId(Number(e.target.value))}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                >
                  {devices.map(d => (
                    <option key={d.id} value={d.id}>
                      {d.name} — {d.brand} ({d.serialNumber})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Nama Pelapor / Staff *</label>
                <input
                  type="text"
                  required
                  placeholder="Nama staff yang melaporkan gangguan"
                  value={reportedBy}
                  onChange={e => setReportedBy(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Deskripsi Kerusakan / Kendala *</label>
                <textarea
                  rows={3}
                  required
                  placeholder="Jelaskan gejala kerusakan, error yang muncul, atau keluhan user..."
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Foto Bukti Kerusakan (Before)</label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    placeholder="URL foto atau pilih file gambar..."
                    value={photoBefore || ''}
                    onChange={e => setPhotoBefore(e.target.value)}
                    className="flex-1 px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none text-xs"
                  />
                  <label className="px-3 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl font-medium text-xs cursor-pointer flex items-center gap-1">
                    <Camera className="w-3.5 h-3.5" />
                    File
                    <input
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={async (e) => {
                        const file = e.target.files?.[0];
                        if (file) {
                          // Quick local preview
                          const reader = new FileReader();
                          reader.onloadend = () => setPhotoBefore(reader.result as string);
                          reader.readAsDataURL(file);

                          // Upload to Cloudflare R2
                          try {
                            const res = await uploadFileToR2(file);
                            if (res.success && res.url) {
                              setPhotoBefore(res.url);
                            }
                          } catch (err) {
                            console.warn('R2 upload failed:', err);
                          }
                        }
                      }}
                    />
                  </label>
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-700 rounded-xl font-medium cursor-pointer"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-xl font-bold cursor-pointer"
                >
                  Kirim Tiket
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Resolve Trouble Ticket Modal */}
      {showResolveModal && selectedTicket && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl space-y-4 my-8">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-bold text-lg text-slate-800 flex items-center gap-2">
                <Wrench className="w-5 h-5 text-[#0F7D3A]" />
                Penyelesaian Trouble Ticket
              </h3>
              <button onClick={() => setShowResolveModal(false)} className="text-slate-400 hover:text-slate-600 cursor-pointer">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleResolveSubmit} className="space-y-4 text-xs sm:text-sm">
              <div className="p-3 bg-slate-50 rounded-xl border border-slate-200 text-xs">
                <p className="font-bold text-slate-800">{selectedTicket.deviceName}</p>
                <p className="text-slate-600 mt-1">Keluhan: "{selectedTicket.description}"</p>
                <p className="text-slate-400 mt-1">Pelapor: {selectedTicket.reportedBy}</p>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Teknisi Yang Menangani</label>
                <select
                  value={technicianName}
                  onChange={e => setTechnicianName(e.target.value)}
                  className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                >
                  {technicians.map(t => (
                    <option key={t.id} value={t.name}>
                      {t.name} ({t.role})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Durasi Pengerjaan</label>
                <select
                  value={duration}
                  onChange={e => setDuration(e.target.value)}
                  className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                >
                  <option value="15 Menit">15 Menit</option>
                  <option value="30 Menit">30 Menit</option>
                  <option value="45 Menit">45 Menit</option>
                  <option value="1 Jam">1 Jam</option>
                  <option value="2 Jam">2 Jam</option>
                  <option value="1 Hari">1 Hari</option>
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Tindakan / Solusi Yang Dilakukan *</label>
                <textarea
                  rows={3}
                  required
                  placeholder="Rincikan tindakan yang dilakukan teknisi hingga perangkat kembali normal..."
                  value={actionTaken}
                  onChange={e => setActionTaken(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Foto Bukti Selesai (After)</label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    placeholder="URL foto bukti perbaikan atau pilih file..."
                    value={photoAfter || ''}
                    onChange={e => setPhotoAfter(e.target.value)}
                    className="flex-1 px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-[#0F7D3A] focus:outline-none text-xs"
                  />
                  <label className="px-3 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl font-medium text-xs cursor-pointer flex items-center gap-1">
                    <Camera className="w-3.5 h-3.5" />
                    File
                    <input
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={async (e) => {
                        const file = e.target.files?.[0];
                        if (file) {
                          // Quick local preview
                          const reader = new FileReader();
                          reader.onloadend = () => setPhotoAfter(reader.result as string);
                          reader.readAsDataURL(file);

                          // Upload to Cloudflare R2
                          try {
                            const res = await uploadFileToR2(file);
                            if (res.success && res.url) {
                              setPhotoAfter(res.url);
                            }
                          } catch (err) {
                            console.warn('R2 upload failed:', err);
                          }
                        }
                      }}
                    />
                  </label>
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowResolveModal(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-700 rounded-xl font-medium cursor-pointer"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-[#0F7D3A] hover:bg-[#0A5527] text-white rounded-xl font-bold cursor-pointer"
                >
                  Simpan & Tandai Selesai
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
