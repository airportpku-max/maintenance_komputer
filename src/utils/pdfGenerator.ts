import jsPDF from 'jspdf';
import autoTable, { CellHookData } from 'jspdf-autotable';
import { Device, MaintenanceLog, TroubleTicket } from '../types';

// Convert any image (SVG, remote URL, etc.) to a standard base64 JPEG/PNG for jsPDF
export async function rasterizeImageToDataUrl(url: string | null | undefined): Promise<string | null> {
  if (!url) return null;
  return new Promise((resolve) => {
    try {
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = () => {
        try {
          const canvas = document.createElement('canvas');
          canvas.width = img.naturalWidth || 400;
          canvas.height = img.naturalHeight || 300;
          const ctx = canvas.getContext('2d');
          if (!ctx) {
            resolve(null);
            return;
          }
          ctx.fillStyle = '#ffffff';
          ctx.fillRect(0, 0, canvas.width, canvas.height);
          ctx.drawImage(img, 0, 0);
          resolve(canvas.toDataURL('image/jpeg', 0.85));
        } catch {
          resolve(null);
        }
      };
      img.onerror = () => resolve(null);
      img.src = url;
    } catch {
      resolve(null);
    }
  });
}

// Draw realistic vector signature stroke if no image provided
function drawVectorSignature(doc: jsPDF, x: number, y: number, width: number, height: number, seed: number = 1) {
  doc.setDrawColor(20, 25, 40);
  doc.setLineWidth(0.35);

  const cx = x + width / 2;
  const cy = y + height / 2;
  const sw = Math.min(width * 0.7, 18);
  const sh = Math.min(height * 0.7, 8);

  const startX = cx - sw / 2;
  const startY = cy + sh * 0.2;

  if (seed % 2 === 0) {
    // Style A: loop signature
    doc.line(startX, startY, startX + sw * 0.3, cy - sh * 0.5);
    doc.line(startX + sw * 0.3, cy - sh * 0.5, startX + sw * 0.5, cy + sh * 0.4);
    doc.line(startX + sw * 0.5, cy + sh * 0.4, startX + sw * 0.7, cy - sh * 0.2);
    doc.line(startX + sw * 0.7, cy - sh * 0.2, startX + sw, cy + sh * 0.1);
  } else {
    // Style B: swoop signature
    doc.line(startX, startY + sh * 0.2, startX + sw * 0.4, cy - sh * 0.6);
    doc.line(startX + sw * 0.4, cy - sh * 0.6, startX + sw * 0.6, cy + sh * 0.3);
    doc.line(startX + sw * 0.2, cy, startX + sw, cy + sh * 0.1);
  }
}

// Draw a sharp vector checkbox in jsPDF (checked ☑ or unchecked ☐)
function drawVectorCheckbox(doc: jsPDF, x: number, y: number, isChecked: boolean) {
  const boxSize = 3.6;
  doc.setDrawColor(0, 0, 0);
  doc.setLineWidth(0.2);
  doc.setFillColor(255, 255, 255);
  doc.rect(x, y, boxSize, boxSize, 'FD');

  if (isChecked) {
    doc.setDrawColor(0, 0, 0);
    doc.setLineWidth(0.3);
    // Draw checkmark lines inside box
    doc.line(x + 0.7, y + 1.8, x + 1.5, y + 2.8);
    doc.line(x + 1.5, y + 2.8, x + 2.9, y + 0.8);
  }
}

// ----------------------------------------------------------------------------------------------------
// 1. GENERATE "FORM CHECKLIST MAINTENANCE" (EXACT MATCH TO ANDROID LANDSCAPE PDF SCREENSHOT)
// ----------------------------------------------------------------------------------------------------
export async function generateOfficialChecklistPdf(
  logs: MaintenanceLog[],
  devices: Device[],
  monthName: string,
  year: number,
  deviceTypeFilter: 'Semua' | 'AIO' | 'Laptop' = 'Semua'
) {
  const doc = new jsPDF({
    orientation: 'landscape',
    unit: 'mm',
    format: 'a4'
  });

  const pageWidth = 297;
  const pageHeight = 210;

  // Pre-rasterize signatures
  const preloadedSigns: Record<number, { tech: string | null; user: string | null }> = {};
  for (const log of logs) {
    const techImg = await rasterizeImageToDataUrl(log.techSignatureData);
    const userImg = await rasterizeImageToDataUrl(log.signatureData);
    preloadedSigns[log.id] = { tech: techImg, user: userImg };
  }

  // Draw Page Blue Outer Border (Matching screenshot's blue page frame)
  const drawPageFrame = () => {
    doc.setDrawColor(30, 64, 175); // Blue border (#1e40af)
    doc.setLineWidth(0.5);
    doc.rect(7, 7, pageWidth - 14, pageHeight - 14);
  };

  drawPageFrame();

  // Document Title (Matching screenshot exact casing and text)
  let titleCategory = 'PC / AIO DELL';
  if (deviceTypeFilter === 'Laptop') {
    titleCategory = 'LAPTOP DELL';
  } else if (deviceTypeFilter === 'Semua') {
    titleCategory = 'PC / LAPTOP DELL';
  }

  const titleText = `FORM CHECKLIST MAINTENANCE ${titleCategory} BANDARA SULTAN SYARIF KASIM II PEKANBARU BULAN ${monthName.toUpperCase()} TAHUN ${year}`;

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(10.5);
  doc.setTextColor(0, 0, 0);
  doc.text(titleText, pageWidth / 2, 14, { align: 'center' });

  // Define two-level multi-column header
  const head = [
    [
      { content: 'No.', rowSpan: 2, styles: { halign: 'center' as const, valign: 'middle' as const } },
      { content: 'Lokasi', rowSpan: 2, styles: { halign: 'center' as const, valign: 'middle' as const } },
      { content: 'User', rowSpan: 2, styles: { halign: 'center' as const, valign: 'middle' as const } },
      { content: 'S/N', rowSpan: 2, styles: { halign: 'center' as const, valign: 'middle' as const } },
      { content: 'Merk', rowSpan: 2, styles: { halign: 'center' as const, valign: 'middle' as const } },
      { content: 'Data Maintenance', colSpan: 8, styles: { halign: 'center' as const, valign: 'middle' as const } },
      { content: 'Tanggal', rowSpan: 2, styles: { halign: 'center' as const, valign: 'middle' as const } },
      { content: 'Paraf Petugas', rowSpan: 2, styles: { halign: 'center' as const, valign: 'middle' as const } },
      { content: 'Paraf User', rowSpan: 2, styles: { halign: 'center' as const, valign: 'middle' as const } },
      { content: 'Keterangan', rowSpan: 2, styles: { halign: 'center' as const, valign: 'middle' as const } }
    ],
    [
      { content: 'Health\nReport', styles: { halign: 'center' as const } },
      { content: 'Disk\nCleanup', styles: { halign: 'center' as const } },
      { content: 'Hardware\nCleanup', styles: { halign: 'center' as const } },
      { content: 'Checking\nDrive\nError', styles: { halign: 'center' as const } },
      { content: 'Scanning\nVirus', styles: { halign: 'center' as const } },
      { content: 'Checking\nNetwork', styles: { halign: 'center' as const } },
      { content: 'Updating\nAntivirus', styles: { halign: 'center' as const } },
      { content: 'Updating\nApplicat.', styles: { halign: 'center' as const } }
    ]
  ];

  // Table Body Rows
  const body = logs.map((log, idx) => {
    const dev = devices.find(d => d.id === log.deviceId);
    const dateObj = new Date(log.timestamp);
    const day = String(dateObj.getDate()).padStart(2, '0');
    const month = String(dateObj.getMonth() + 1).padStart(2, '0');
    const dateStr = `${day}/${month}/${dateObj.getFullYear()}`;

    return [
      String(idx + 1),
      dev?.description || 'Kantor Admin API/GAGS',
      log.deviceName.split('(')[0].trim(),
      dev?.sn || dev?.serialNumber || '-',
      dev?.brand || 'Dell',
      log.healthReport ? '1' : '0',
      log.diskCleanup ? '1' : '0',
      log.hardwareCleanup ? '1' : '0',
      log.checkingDriveError ? '1' : '0',
      log.scanningVirus ? '1' : '0',
      log.checkingNetwork ? '1' : '0',
      log.updatingAntivirus ? '1' : '0',
      log.updatingAplikasi ? '1' : '0',
      dateStr,
      String(log.id), // Paraf Petugas marker
      String(log.id), // Paraf User marker
      log.notes || 'baik'
    ];
  });

  autoTable(doc, {
    startY: 18,
    head: head,
    body: body,
    theme: 'plain',
    styles: {
      fontSize: 7,
      cellPadding: 1.5,
      textColor: [0, 0, 0],
      lineColor: [0, 0, 0],
      lineWidth: 0.2,
      valign: 'middle'
    },
    headStyles: {
      fillColor: [206, 229, 246], // Light soft blue (#CEE5F6) matching screenshot
      textColor: [0, 0, 0],
      fontStyle: 'bold',
      lineColor: [0, 0, 0],
      lineWidth: 0.2
    },
    columnStyles: {
      0: { halign: 'center', cellWidth: 8 },    // No
      1: { halign: 'left', cellWidth: 32 },      // Lokasi
      2: { halign: 'center', cellWidth: 26 },    // User
      3: { halign: 'center', cellWidth: 22 },    // S/N
      4: { halign: 'center', cellWidth: 15 },    // Merk
      5: { halign: 'center', cellWidth: 10 },    // Health Report
      6: { halign: 'center', cellWidth: 10 },    // Disk Cleanup
      7: { halign: 'center', cellWidth: 10 },    // Hardware Cleanup
      8: { halign: 'center', cellWidth: 10 },    // Checking Drive Error
      9: { halign: 'center', cellWidth: 10 },    // Scanning Virus
      10: { halign: 'center', cellWidth: 10 },   // Checking Network
      11: { halign: 'center', cellWidth: 10 },   // Updating Antivirus
      12: { halign: 'center', cellWidth: 10 },   // Updating Applicat.
      13: { halign: 'center', cellWidth: 20 },   // Tanggal
      14: { halign: 'center', cellWidth: 24 },   // Paraf Petugas
      15: { halign: 'center', cellWidth: 24 },   // Paraf User
      16: { halign: 'center', cellWidth: 22 }    // Keterangan
    },
    didDrawCell: (data: CellHookData) => {
      // 1. Draw checkboxes for Data Maintenance (cols 5 to 12)
      if (data.section === 'body' && data.column.index >= 5 && data.column.index <= 12) {
        const isChecked = data.cell.raw === '1';
        const cbX = data.cell.x + (data.cell.width - 3.6) / 2;
        const cbY = data.cell.y + (data.cell.height - 3.6) / 2;
        drawVectorCheckbox(doc, cbX, cbY, isChecked);
      }

      // 2. Draw signatures for Paraf Petugas (col 14) and Paraf User (col 15)
      if (data.section === 'body' && (data.column.index === 14 || data.column.index === 15)) {
        const logId = Number(data.cell.raw);
        const signs = preloadedSigns[logId];
        const isPetugas = data.column.index === 14;
        const signImg = isPetugas ? signs?.tech : signs?.user;

        const cellPadding = 1.5;
        const w = data.cell.width - cellPadding * 2;
        const h = data.cell.height - cellPadding * 2;
        const x = data.cell.x + cellPadding;
        const y = data.cell.y + cellPadding;

        if (signImg) {
          try {
            doc.addImage(signImg, 'JPEG', x + (w - 18) / 2, y + (h - 7) / 2, 18, 7);
          } catch {
            drawVectorSignature(doc, x, y, w, h, logId + (isPetugas ? 1 : 2));
          }
        } else {
          drawVectorSignature(doc, x, y, w, h, logId + (isPetugas ? 1 : 2));
        }
      }
    },
    didDrawPage: () => {
      drawPageFrame();
    }
  });

  // Calculate position for official bottom signatures
  const finalTableY = (doc as any).lastAutoTable ? (doc as any).lastAutoTable.finalY : 120;
  const signBlockY = Math.max(finalTableY + 16, 148);

  // Left Signatory (Pengawas)
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8.5);
  doc.setTextColor(0, 0, 0);
  doc.text('Pengawas,', 55, signBlockY, { align: 'center' });

  doc.setFont('helvetica', 'bold');
  doc.text('AIRPORT TECHNOLOGY ENGINEER', 55, signBlockY + 5, { align: 'center' });

  // Underlined Name
  doc.setFont('helvetica', 'bold');
  doc.text('ARIFATUL AZHAR', 55, signBlockY + 28, { align: 'center' });
  doc.setLineWidth(0.2);
  doc.line(35, signBlockY + 29, 75, signBlockY + 29);

  // Right Signatory (Mengetahui)
  const todayDateStr = `Pekanbaru, 10 September ${year}`;
  doc.setFont('helvetica', 'normal');
  doc.text(todayDateStr, 235, signBlockY - 5, { align: 'center' });
  doc.text('Mengetahui,', 235, signBlockY, { align: 'center' });

  doc.setFont('helvetica', 'bold');
  doc.text('AIRPORT TECHNOLOGY DEPARTMENT HEAD', 235, signBlockY + 5, { align: 'center' });

  // Underlined Name
  doc.setFont('helvetica', 'bold');
  doc.text('EKO ARIF RAHMANTO', 235, signBlockY + 28, { align: 'center' });
  doc.setLineWidth(0.2);
  doc.line(212, signBlockY + 29, 258, signBlockY + 29);

  // Save PDF
  doc.save(`Form_Checklist_Maintenance_${titleCategory.replace(/\s+/g, '_')}_${monthName}_${year}.pdf`);
}

// ----------------------------------------------------------------------------------------------------
// 2. GENERATE "LAMPIRAN FOTO PERAWATAN" (EXACT MATCH TO ANDROID 10-PAGE PORTRAIT PDF SCREENSHOTS)
// ----------------------------------------------------------------------------------------------------
export async function generateOfficialPhotoReportPdf(
  logs: MaintenanceLog[],
  devices: Device[],
  monthName: string,
  year: number
) {
  const doc = new jsPDF({
    orientation: 'portrait',
    unit: 'mm',
    format: 'a4'
  });

  const pageWidth = 210;
  const pageHeight = 297;

  // Group logs by date (or device)
  const logsToProcess = logs.length > 0 ? logs : [];

  // Pre-rasterize photos
  const photoCache: Record<number, { before: string | null; after: string | null }> = {};
  for (const log of logsToProcess) {
    const beforeImg = await rasterizeImageToDataUrl(log.healthReportBeforePhoto || log.hardwareCleanupBeforePhoto);
    const afterImg = await rasterizeImageToDataUrl(log.healthReportAfterPhoto || log.hardwareCleanupAfterPhoto);
    photoCache[log.id] = { before: beforeImg, after: afterImg };
  }

  // 8 Inspection Items in exact Indonesian wording and order matching screenshot:
  const inspectionItems = [
    // Page 1 Items (Items 1 - 4)
    { id: 'hr', name: 'Health Report' },
    { id: 'dc', name: 'Disk CleanUp' },
    { id: 'hc', name: 'Hardware CleanUp' },
    { id: 'de', name: 'Checking Drive Error' },
    // Page 2 Items (Items 5 - 8)
    { id: 'sv', name: 'Scanning Virus' },
    { id: 'cn', name: 'Checking Network' },
    { id: 'av', name: 'Updating Antivirus Databases' },
    { id: 'ap', name: 'Updating Aplikasi' }
  ];

  let isFirstPage = true;

  for (let logIndex = 0; logIndex < logsToProcess.length; logIndex++) {
    const log = logsToProcess[logIndex];
    const dateObj = new Date(log.timestamp);
    const dayStr = String(dateObj.getDate()).padStart(2, '0');
    const dateTitle = `Tanggal : ${dayStr} ${monthName} ${year}`;
    const photos = photoCache[log.id];

    // Each log generates 2 portrait pages (4 items on Page 1, 4 items on Page 2)
    // -------------------------------------------------------------
    // PAGE 1: Items 1 - 4
    // -------------------------------------------------------------
    if (!isFirstPage) {
      doc.addPage();
    }
    isFirstPage = false;

    // Header Title (Only on Page 1 of each unit)
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11);
    doc.setTextColor(0, 0, 0);
    doc.text('LAMPIRAN FOTO PERAWATAN LAPTOP & PC AIO', pageWidth / 2, 13, { align: 'center' });

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9.5);
    doc.text(dateTitle, pageWidth / 2, 18, { align: 'center' });

    // Table Page 1 (4 rows, height ~ 56mm each)
    const page1Items = inspectionItems.slice(0, 4);
    const page1Body = page1Items.map(item => [item.name, '', '']);

    autoTable(doc, {
      startY: 23,
      head: [['JENIS PEMERIKSAAN', 'SEBELUM', 'SESUDAH']],
      body: page1Body,
      theme: 'plain',
      styles: {
        fontSize: 8.5,
        textColor: [0, 0, 0],
        lineColor: [0, 0, 0],
        lineWidth: 0.25,
        valign: 'middle',
        halign: 'center',
        cellPadding: 2,
        minCellHeight: 56.5
      },
      headStyles: {
        fillColor: [255, 255, 255],
        textColor: [0, 0, 0],
        fontStyle: 'bold',
        lineColor: [0, 0, 0],
        lineWidth: 0.25,
        minCellHeight: 8
      },
      columnStyles: {
        0: { cellWidth: 62, fontStyle: 'normal' },
        1: { cellWidth: 67 },
        2: { cellWidth: 67 }
      },
      didDrawCell: (data: CellHookData) => {
        if (data.section === 'body') {
          // Columns 1 and 2: Draw Photo SEBELUM and SESUDAH
          if (data.column.index === 1 || data.column.index === 2) {
            const isBefore = data.column.index === 1;
            const imgData = isBefore ? photos?.before : photos?.after;

            const padX = 2;
            const padY = 2;
            const w = data.cell.width - padX * 2;
            const h = data.cell.height - padY * 2;
            const x = data.cell.x + padX;
            const y = data.cell.y + padY;

            if (imgData) {
              try {
                doc.addImage(imgData, 'JPEG', x, y, w, h);
              } catch {
                // Fallback placeholder box
                doc.setDrawColor(200, 200, 200);
                doc.rect(x, y, w, h);
              }
            } else {
              // Fallback placeholder box
              doc.setDrawColor(220, 225, 230);
              doc.rect(x, y, w, h);
            }
          }
        }
      }
    });

    // -------------------------------------------------------------
    // PAGE 2: Items 5 - 8
    // -------------------------------------------------------------
    doc.addPage();

    // Table Page 2 (Header starts directly at top)
    const page2Items = inspectionItems.slice(4, 8);
    const page2Body = page2Items.map(item => [item.name, '', '']);

    autoTable(doc, {
      startY: 12,
      head: [['JENIS PEMERIKSAAN', 'SEBELUM', 'SESUDAH']],
      body: page2Body,
      theme: 'plain',
      styles: {
        fontSize: 8.5,
        textColor: [0, 0, 0],
        lineColor: [0, 0, 0],
        lineWidth: 0.25,
        valign: 'middle',
        halign: 'center',
        cellPadding: 2,
        minCellHeight: 58
      },
      headStyles: {
        fillColor: [255, 255, 255],
        textColor: [0, 0, 0],
        fontStyle: 'bold',
        lineColor: [0, 0, 0],
        lineWidth: 0.25,
        minCellHeight: 8
      },
      columnStyles: {
        0: { cellWidth: 62, fontStyle: 'normal' },
        1: { cellWidth: 67 },
        2: { cellWidth: 67 }
      },
      didDrawCell: (data: CellHookData) => {
        if (data.section === 'body') {
          if (data.column.index === 1 || data.column.index === 2) {
            const isBefore = data.column.index === 1;
            const imgData = isBefore ? photos?.before : photos?.after;

            const padX = 2;
            const padY = 2;
            const w = data.cell.width - padX * 2;
            const h = data.cell.height - padY * 2;
            const x = data.cell.x + padX;
            const y = data.cell.y + padY;

            if (imgData) {
              try {
                doc.addImage(imgData, 'JPEG', x, y, w, h);
              } catch {
                doc.setDrawColor(200, 200, 200);
                doc.rect(x, y, w, h);
              }
            } else {
              doc.setDrawColor(220, 225, 230);
              doc.rect(x, y, w, h);
            }
          }
        }
      }
    });
  }

  // Save PDF
  doc.save(`Lampiran_Foto_Perawatan_Laptop_AIO_${monthName}_${year}.pdf`);
}

// ----------------------------------------------------------------------------------------------------
// 3. GENERATE TROUBLE TICKET / CORRECTIVE MAINTENANCE (CM)
// ----------------------------------------------------------------------------------------------------
export async function generateOfficialTroublePdf(
  tickets: TroubleTicket[],
  monthName: string,
  year: number
) {
  const doc = new jsPDF({
    orientation: 'landscape',
    unit: 'mm',
    format: 'a4'
  });

  const pageWidth = 297;
  const pageHeight = 210;

  // Blue page frame
  doc.setDrawColor(30, 64, 175);
  doc.setLineWidth(0.5);
  doc.rect(7, 7, pageWidth - 14, pageHeight - 14);

  // Title
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.text(
    `LAPORAN PENANGANAN GANGGUAN KOMPUTER (CORRECTIVE MAINTENANCE) BANDARA SSK II ${monthName.toUpperCase()} ${year}`,
    pageWidth / 2,
    14,
    { align: 'center' }
  );

  const head = [['No', 'Perangkat & Merk', 'Pelapor / Unit', 'Tanggal', 'Gejala Kerusakan', 'Tindakan Perbaikan', 'Durasi', 'Status']];
  const body = tickets.map((t, idx) => [
    idx + 1,
    t.deviceName,
    t.reportedBy,
    new Date(t.timestamp).toLocaleDateString('id-ID'),
    t.description,
    t.actionTaken || 'Pemeriksaan & perbaikan komponen',
    t.duration || '30 Menit',
    t.status
  ]);

  autoTable(doc, {
    startY: 18,
    head: head,
    body: body,
    theme: 'plain',
    styles: {
      fontSize: 8,
      cellPadding: 2.5,
      textColor: [0, 0, 0],
      lineColor: [0, 0, 0],
      lineWidth: 0.2
    },
    headStyles: {
      fillColor: [206, 229, 246],
      textColor: [0, 0, 0],
      fontStyle: 'bold',
      lineColor: [0, 0, 0],
      lineWidth: 0.2,
      halign: 'center'
    },
    columnStyles: {
      0: { halign: 'center', cellWidth: 10 },
      1: { cellWidth: 42 },
      2: { cellWidth: 42 },
      3: { halign: 'center', cellWidth: 22 },
      4: { cellWidth: 55 },
      5: { cellWidth: 55 },
      6: { halign: 'center', cellWidth: 18 },
      7: { halign: 'center', cellWidth: 20, fontStyle: 'bold' }
    }
  });

  const finalTableY = (doc as any).lastAutoTable ? (doc as any).lastAutoTable.finalY : 120;
  const signBlockY = Math.max(finalTableY + 16, 148);

  // Signatures
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8.5);
  doc.text('Pengawas,', 55, signBlockY, { align: 'center' });
  doc.setFont('helvetica', 'bold');
  doc.text('AIRPORT TECHNOLOGY ENGINEER', 55, signBlockY + 5, { align: 'center' });
  doc.text('ARIFATUL AZHAR', 55, signBlockY + 28, { align: 'center' });
  doc.line(35, signBlockY + 29, 75, signBlockY + 29);

  const todayDateStr = `Pekanbaru, 10 September ${year}`;
  doc.setFont('helvetica', 'normal');
  doc.text(todayDateStr, 235, signBlockY - 5, { align: 'center' });
  doc.text('Mengetahui,', 235, signBlockY, { align: 'center' });
  doc.setFont('helvetica', 'bold');
  doc.text('AIRPORT TECHNOLOGY DEPARTMENT HEAD', 235, signBlockY + 5, { align: 'center' });
  doc.text('EKO ARIF RAHMANTO', 235, signBlockY + 28, { align: 'center' });
  doc.line(212, signBlockY + 29, 258, signBlockY + 29);

  doc.save(`Laporan_Trouble_CM_Bandara_SSK_II_${monthName}_${year}.pdf`);
}
