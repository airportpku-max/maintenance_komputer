import React from 'react';

interface BarcodeVisualProps {
  value: string;
  type?: 'barcode' | 'qr';
  width?: number;
  height?: number;
}

export const BarcodeVisual: React.FC<BarcodeVisualProps> = ({
  value,
  type = 'barcode',
  width = 240,
  height = 70
}) => {
  if (type === 'barcode') {
    // Deterministic pseudo-Code128 stripe visual based on characters
    const hash = value.split('').reduce((acc, char, idx) => acc + char.charCodeAt(0) * (idx + 1), 0);
    const bars: boolean[] = [];
    for (let i = 0; i < 45; i++) {
      const bit = ((hash * (i + 7)) % 11) > 4;
      bars.push(bit);
    }

    return (
      <div className="flex flex-col items-center bg-white p-3 rounded-lg border border-slate-200 shadow-sm">
        <div className="flex items-end justify-center h-12 gap-[3px] w-full max-w-[240px] px-2 py-1">
          {bars.map((isDark, i) => (
            <div
              key={i}
              className={`h-full ${isDark ? 'bg-black w-[3px]' : 'bg-transparent w-[2px]'}`}
            />
          ))}
        </div>
        <span className="font-mono text-xs tracking-widest font-bold text-slate-800 mt-1">
          {value}
        </span>
      </div>
    );
  }

  // QR representation
  return (
    <div className="flex flex-col items-center bg-white p-3 rounded-lg border border-slate-200 shadow-sm">
      <div className="w-32 h-32 border-2 border-slate-900 p-1 flex flex-col justify-between relative bg-white">
        {/* QR Corner squares */}
        <div className="flex justify-between">
          <div className="w-7 h-7 border-4 border-black flex items-center justify-center">
            <div className="w-3 h-3 bg-black" />
          </div>
          <div className="w-7 h-7 border-4 border-black flex items-center justify-center">
            <div className="w-3 h-3 bg-black" />
          </div>
        </div>
        <div className="absolute inset-x-8 inset-y-8 flex flex-wrap gap-1 p-1 items-center justify-center">
          <div className="w-2 h-2 bg-black" />
          <div className="w-1.5 h-1.5 bg-black" />
          <div className="w-2 h-2 bg-black" />
          <div className="w-1.5 h-1.5 bg-black" />
          <div className="w-2 h-2 bg-black" />
          <div className="w-2 h-2 bg-black" />
        </div>
        <div className="flex justify-start">
          <div className="w-7 h-7 border-4 border-black flex items-center justify-center">
            <div className="w-3 h-3 bg-black" />
          </div>
        </div>
      </div>
      <span className="font-mono text-[11px] font-bold text-slate-700 mt-1.5">
        {value}
      </span>
    </div>
  );
};
