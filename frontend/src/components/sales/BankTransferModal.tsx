import { QRCodeSVG } from 'qrcode.react';
import { CheckCircle2, Copy } from 'lucide-react';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { useToast } from '../ui/Toast';
import { formatVND } from '../../utils/formatters';
import { bankAccount, vietQrPayload } from '../../utils/vietqr';

/** Mã QR chuyển khoản cho khách quét; thu ngân bấm xác nhận sau khi thấy tiền về. Không tự kiểm tra với ngân hàng. */
export function BankTransferModal({ isOpen, total, code, paying, onConfirm, onClose }: {
  isOpen: boolean;
  total: number;
  code: string;
  paying: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const toast = useToast();
  const bank = bankAccount();
  // Chưa cấu hình tài khoản: chỉ mã hóa chữ, app ngân hàng không chuyển tiền được
  const payload = bank ? vietQrPayload(bank, total, code) : `SIEU THI UY TIN DEMO - ${Math.round(total)} VND - ${code}`;

  const copy = async () => {
    try { await navigator.clipboard.writeText(code); toast.success('Đã chép nội dung chuyển khoản'); }
    catch { toast.warning('Trình duyệt không cho chép, vui lòng nhập tay'); }
  };

  return (
    <Modal isOpen={isOpen} onClose={() => { if (!paying) onClose(); }} title="Quét mã để chuyển khoản" size="sm">
      <div className="flex flex-col items-center gap-3 text-center">
        {/* Nền trắng cố định để máy quét đọc được ở cả giao diện tối */}
        <div className="rounded-lg border border-gray-200 p-3" style={{ background: '#fff' }}>
          <QRCodeSVG value={payload} size={208} level="M" marginSize={2} title="Mã QR chuyển khoản" />
        </div>
        <p className="text-2xl font-bold">{formatVND(total)}</p>
        {bank && <p className="text-sm text-gray-600">{bank.owner && <b className="block">{bank.owner}</b>}Số TK: {bank.account}</p>}
        <div className="flex items-center gap-2 text-sm">
          <span>Nội dung: <b className="font-mono">{code}</b></span>
          <Button size="sm" variant="ghost" aria-label="Chép nội dung" onClick={copy}><Copy className="h-4 w-4" /></Button>
        </div>
        <Button size="lg" className="w-full" isLoading={paying} onClick={onConfirm} leftIcon={<CheckCircle2 className="h-4 w-4" />}>Đã nhận chuyển khoản</Button>
        <Button variant="outline" className="w-full" disabled={paying} onClick={onClose}>Quay lại</Button>
        <p className="text-xs text-gray-500">Kiểm tra tiền đã về tài khoản rồi mới xác nhận.</p>
      </div>
    </Modal>
  );
}
