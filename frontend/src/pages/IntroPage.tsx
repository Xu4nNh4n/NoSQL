import React from 'react';
import { Link } from 'react-router-dom';
import { Columns3, Database, Share2, Zap } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { Card } from '../components/ui/Card';
import { Table, Td } from '../components/ui/Table';
import type { RoleName } from '../types';

type DbInfo = {
  name: string;
  kind: string;
  icon: React.ComponentType<{ className?: string }>;
  tagline: string;
  jobs: string[];
  stores: string;
  why: string;
  down: string;
  link: { to: string; label: string; roles: RoleName[] };
};

const DBS: DbInfo[] = [
  {
    name: 'MongoDB',
    kind: 'CSDL tài liệu (document)',
    icon: Database,
    tagline: 'Cuốn sổ chính: mọi dữ liệu gốc của siêu thị nằm ở đây.',
    jobs: [
      'Lưu sản phẩm, lô hàng, phiếu nhập, đơn bán, phiếu trả, khuyến mãi, người dùng',
      'Bán hàng bằng transaction: trừ tồn và tạo đơn cùng lúc, không bán quá số lượng',
      'Thống kê doanh thu bằng aggregation ($facet)',
    ],
    stores: 'products, batches, orders… Một đơn chứa luôn dòng hàng, lô đã xuất và thanh toán',
    why: 'Mỗi sản phẩm có thuộc tính khác nhau (sữa có dung tích, nồi cơm có bảo hành) nên cần cấu trúc linh hoạt',
    down: 'Hệ thống ngừng bán vì mất dữ liệu gốc',
    link: { to: '/orders', label: 'Xem đơn hàng', roles: ['ADMIN', 'STAFF'] },
  },
  {
    name: 'Cassandra',
    kind: 'CSDL cột rộng (wide-column)',
    icon: Columns3,
    tagline: 'Cuốn nhật ký chỉ ghi thêm: chuyện gì đã xảy ra, vào lúc nào.',
    jobs: [
      'Thẻ kho: mọi lần nhập, bán, trả của từng sản phẩm theo tháng',
      'Sổ thanh toán theo đơn và theo thu ngân mỗi ngày, dùng để chốt ca',
      'Lịch sử đăng nhập, tự xóa sau 90 ngày (TTL)',
    ],
    stores: '4 bảng: stock_movements_by_product, payments_by_order, payments_by_cashier_day, login_history_by_user',
    why: 'Ghi rất nhanh và đọc theo đúng khóa phân vùng, hợp với dữ liệu nhật ký tăng liên tục',
    down: 'Mất thẻ kho, chốt ca và lịch sử đăng nhập; bán hàng vẫn chạy',
    link: { to: '/products', label: 'Xem thẻ kho', roles: ['ADMIN', 'STAFF', 'WAREHOUSE'] },
  },
  {
    name: 'Neo4j',
    kind: 'CSDL đồ thị (graph)',
    icon: Share2,
    tagline: 'Tấm bản đồ quan hệ: ai mua gì, hàng lấy từ lô nào, của nhà cung cấp nào.',
    jobs: [
      'Truy vết lô lỗi: tìm mọi đơn đã bán hàng của lô đó để thu hồi',
      'Gợi ý mua kèm: khách mua món này thường mua thêm món gì',
    ],
    stores: '(Order)-[CONTAINS]->(Product), (Order)-[TOOK_FROM]->(Batch), (Batch)-[OF_PRODUCT]->(Product), (Batch)-[SUPPLIED_BY]->(Supplier)',
    why: 'Câu hỏi đi qua nhiều mối quan hệ, viết bằng Cypher ngắn và không cần join nhiều bảng',
    down: 'Mất truy vết và gợi ý; đồ thị dựng lại được từ MongoDB',
    link: { to: '/inventory', label: 'Truy vết theo lô', roles: ['ADMIN'] },
  },
  {
    name: 'Redis',
    kind: 'CSDL khóa – giá trị trong RAM (key-value)',
    icon: Zap,
    tagline: 'Tờ giấy nháp siêu nhanh: dữ liệu tạm, hết hạn thì tự xóa.',
    jobs: [
      'Giữ phiên đăng nhập (refresh token dùng một lần)',
      'Cache sản phẩm và khuyến mãi cho quầy bán',
      'Đơn treo 30 phút và bảng top bán chạy trong ngày',
    ],
    stores: 'auth:refresh:*, cache:products:active, pos:held:*, top:sales:yyyyMMdd (sorted set)',
    why: 'Nằm trong RAM nên đọc ghi rất nhanh, có sẵn TTL và sorted set để xếp hạng',
    down: 'Mất đơn treo và top bán; phiên đăng nhập không gia hạn được; bán hàng vẫn chạy',
    link: { to: '/pos', label: 'Xem top bán chạy', roles: ['STAFF'] },
  },
];

const STEPS = [
  { db: 'Redis', text: 'Thu ngân mở quầy: danh sách sản phẩm và khuyến mãi đọc từ cache.' },
  { db: 'Neo4j', text: 'Thêm món vào giỏ: hiện các món khách hay mua kèm.' },
  { db: 'MongoDB', text: 'Bấm thanh toán: một transaction chọn lô gần hết hạn trước (FEFO), trừ tồn, tạo đơn và xếp việc vào sync_queue.' },
  { db: 'Cassandra', text: 'Sau khi lưu xong: ghi thêm dòng thẻ kho và dòng thanh toán.' },
  { db: 'Neo4j', text: 'Cùng lúc đó: nối đơn với sản phẩm và lô đã xuất trên đồ thị.' },
  { db: 'Redis', text: 'Cộng số lượng vào bảng top bán chạy hôm nay, xóa cache cũ.' },
];

/** Giới thiệu vai trò của 4 CSDL bằng lời dễ hiểu; nội dung tĩnh nên vai trò nào cũng xem được. */
export function IntroPage() {
  const { role } = useAuth();

  return (
    <div className="space-y-4">
      <div className="ui-card ui-login rounded-lg border border-gray-200 bg-white p-6">
        <p className="tag-accent">Đồ án NoSQL</p>
        <h1 className="mt-2 text-2xl font-extrabold tracking-tight">4 cơ sở dữ liệu, mỗi cái một việc</h1>
        <p className="mt-2 max-w-3xl text-sm text-gray-600">
          Hệ thống chia dữ liệu cho 4 loại NoSQL, mỗi loại làm việc mà nó giỏi nhất. MongoDB giữ dữ liệu gốc;
          Cassandra và Neo4j được cập nhật theo sau qua hàng đợi đồng bộ; Redis giữ dữ liệu tạm.
        </p>
        <div className="mt-4 grid grid-cols-2 gap-2 text-xs sm:grid-cols-4">
          {DBS.map(d => (
            <span key={d.name} className="ui-db-chip rounded-md border border-gray-200 px-2 py-2 text-center font-semibold">{d.name}</span>
          ))}
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        {DBS.map(d => {
          const Icon = d.icon;
          return (
            <Card key={d.name} title={`${d.name} · ${d.kind}`} action={<Icon className="h-5 w-5 text-gray-500" />}>
              <p className="text-sm font-semibold">{d.tagline}</p>
              <p className="eyebrow mt-4">Làm việc gì</p>
              <ul className="mt-1 list-disc space-y-1 pl-5 text-sm text-gray-700">
                {d.jobs.map(j => <li key={j}>{j}</li>)}
              </ul>
              <p className="eyebrow mt-4">Lưu những gì</p>
              <p className="mt-1 text-sm break-words text-gray-700">{d.stores}</p>
              <p className="eyebrow mt-4">Vì sao chọn</p>
              <p className="mt-1 text-sm text-gray-700">{d.why}</p>
              {role && d.link.roles.includes(role) && (
                <Link to={d.link.to} className="mt-4 inline-block text-sm font-semibold text-blue-700 underline">{d.link.label} →</Link>
              )}
            </Card>
          );
        })}
      </div>

      <Card title="Một đơn bán đi qua 4 CSDL như thế nào">
        <ol className="space-y-3">
          {STEPS.map((s, i) => (
            <li key={s.text} className="flex items-start gap-3 text-sm">
              <span className="ui-page ui-page-active flex h-7 w-7 shrink-0 items-center justify-center rounded-md border border-gray-300 font-semibold">{i + 1}</span>
              <span className="pt-1"><b>{s.db}</b> · {s.text}</span>
            </li>
          ))}
        </ol>
      </Card>

      <Card title="Nếu một CSDL bị tắt thì sao">
        <Table headers={['CSDL', 'Điều xảy ra']}>
          {DBS.map(d => (
            <tr key={d.name}><Td className="font-semibold">{d.name}</Td><Td>{d.down}</Td></tr>
          ))}
        </Table>
      </Card>
    </div>
  );
}
