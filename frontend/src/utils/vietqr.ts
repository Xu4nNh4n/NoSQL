/** Tài khoản nhận tiền, đặt trong .env ở gốc dự án (VITE_BANK_*). Thiếu hoặc sai dạng thì null. */
export type BankAccount = { bin: string; account: string; owner: string };

export function bankAccount(): BankAccount | null {
  const bin = (import.meta.env.VITE_BANK_BIN ?? '').trim();
  const account = (import.meta.env.VITE_BANK_ACCOUNT ?? '').trim();
  const owner = (import.meta.env.VITE_BANK_OWNER ?? '').trim();
  return /^\d{6}$/.test(bin) && /^[A-Za-z0-9]{4,19}$/.test(account) ? { bin, account, owner } : null;
}

const field = (id: string, value: string) => id + String(value.length).padStart(2, '0') + value;

/** CRC-16/CCITT-FALSE, dùng cho trường 63 của EMVCo QR. */
export function crc16(text: string): string {
  let crc = 0xffff;
  for (let i = 0; i < text.length; i++) {
    crc ^= text.charCodeAt(i) << 8;
    for (let b = 0; b < 8; b++) crc = (crc & 0x8000 ? (crc << 1) ^ 0x1021 : crc << 1) & 0xffff;
  }
  return crc.toString(16).toUpperCase().padStart(4, '0');
}

/** Chuỗi VietQR động (NAPAS 247, chuyển tới tài khoản) có sẵn số tiền và nội dung. */
export function vietQrPayload(to: { bin: string; account: string }, amount: number, content: string): string {
  const beneficiary = field('00', 'A000000727') + field('01', field('00', to.bin) + field('01', to.account)) + field('02', 'QRIBFTTA');
  const body = field('00', '01') + field('01', '12') + field('38', beneficiary) + field('53', '704')
    + field('54', String(Math.round(amount))) + field('58', 'VN') + field('62', field('08', content)) + '6304';
  return body + crc16(body);
}

/** Nội dung chuyển khoản, cũng là mã giao dịch của đơn: SM + ngày tháng + 6 ký tự (bỏ 0/O, 1/I dễ nhầm). */
export function newTransferCode(dateKey: string): string {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const random = crypto.getRandomValues(new Uint8Array(6));
  return 'SM' + dateKey.slice(8, 10) + dateKey.slice(5, 7) + Array.from(random, n => alphabet[n % alphabet.length]).join('');
}
