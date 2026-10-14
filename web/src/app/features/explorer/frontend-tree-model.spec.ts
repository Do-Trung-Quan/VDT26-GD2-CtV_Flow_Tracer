import type { UiTreeNodeDto } from '../../core/api/api-types';
import {
  countByApi,
  countElements,
  findTrail,
  flattenRows,
  fullLabel,
  highlightFold,
  isContainer,
  nodeIcon,
  nodeLabel,
  isApiStep,
  pruneToApi,
  revealIds,
  searchUi
} from './frontend-tree-model';

const el = (key: string, over: Partial<UiTreeNodeDto> = {}): UiTreeNodeDto => ({
  nodeKey: key,
  kind: 'UI_ELEMENT',
  name: key,
  elementType: 'button',
  children: [],
  ...over
});

const tree: UiTreeNodeDto[] = [
  {
    nodeKey: 'app',
    kind: 'UI_APP',
    name: 'admin-app',
    children: [
      {
        nodeKey: 'scr',
        kind: 'UI_SCREEN',
        name: 'CategoryList',
        displayName: 'Danh mục',
        routePath: '/categories',
        children: [
          el('add', { displayName: 'Thêm mới' }),
          el('del', { displayName: 'Xóa', callsApi: true }),
          el('icon', { fallbackName: 'onRefresh', name: 'onRefresh' }),
          {
            nodeKey: 'dlg',
            kind: 'UI_DIALOG',
            name: 'FormDialog',
            displayName: 'Thêm danh mục',
            children: [el('code', { elementType: 'input', displayName: 'Mã' }), el('add')]
          }
        ]
      }
    ]
  }
];

describe('nhãn nút trên cây Frontend', () => {
  it('có nhãn thì "<loại> <nhãn>" theo quy ước của bản vẽ', () => {
    const scr = tree[0].children![0];
    expect(fullLabel(scr)).toBe('Màn hình Danh mục');
    expect(fullLabel(scr.children![0])).toBe('Nút Thêm mới');
    expect(fullLabel(scr.children![3].children![0])).toBe('Ô Mã');
    expect(nodeLabel(scr.children![0]).technical).toBe(false);
    expect(fullLabel(tree[0])).toBe('Ứng dụng admin-app');
  });

  it('không có nhãn thì dùng tên kỹ thuật và đánh dấu technical', () => {
    const icon = tree[0].children![0].children![2];
    expect(nodeLabel(icon)).toEqual({ prefix: 'Nút', text: 'onRefresh', technical: true });
  });

  it('form không có nhãn chỉ giữ tên hàm gửi', () => {
    expect(nodeLabel({ kind: 'UI_FORM', name: 'form.ngSubmit.onSubmit' })).toEqual({ prefix: 'Form', text: 'onSubmit', technical: true });
    expect(nodeLabel({ kind: 'UI_FORM', name: 'form.formGroup', displayName: 'Thông tin tác giả' }).text).toBe('Thông tin tác giả');
  });

  it('màn hình không có nhãn dùng đường dẫn route', () => {
    expect(nodeLabel({ kind: 'UI_SCREEN', name: 'X', routePath: '/orders' })).toEqual({
      prefix: 'Màn hình',
      text: '/orders',
      technical: true
    });
  });

  it('icon và phân loại thành phần chứa', () => {
    const scr = tree[0].children![0];
    expect(nodeIcon(tree[0])).toBe('layers');
    expect(nodeIcon(scr)).toBe('monitor');
    expect(nodeIcon(scr.children![3])).toBe('window');
    expect(nodeIcon(scr.children![3].children![0])).toBe('input');
    expect(nodeIcon(scr.children![0])).toBe('cursor');
    expect(isContainer(scr)).toBe(true);
    expect(isContainer(scr.children![0])).toBe(false);
  });
});

describe('lọc và tìm trong cây Frontend', () => {
  it('từ khóa rỗng không có kết quả', () => {
    expect(searchUi(tree, '  ')).toEqual({ screens: [], elements: [], screenTotal: 0, elementTotal: 0 });
  });

  it('chia hai nhóm: màn hình (gồm hộp thoại) và phần tử', () => {
    const r = searchUi(tree, 'them');
    expect(r.screens.map((h) => h.node.nodeKey)).toEqual(['dlg']);
    expect(r.elements.map((h) => h.node.nodeKey)).toEqual(['add']);
    expect(r.elements[0].context).toBe('Màn hình Danh mục');
    expect(r.elements[0].trail.map((n) => n.nodeKey)).toEqual(['app', 'scr']);
  });

  it('không phân biệt dấu và hoa thường, mọi từ đều phải khớp', () => {
    expect(searchUi(tree, 'XOA').elements.map((h) => h.node.nodeKey)).toEqual(['del']);
    expect(searchUi(tree, 'them moi').elements.map((h) => h.node.nodeKey)).toEqual(['add']);
    expect(searchUi(tree, 'them khong-co').elements).toEqual([]);
  });

  it('khớp theo tên do AI đặt, tên kỹ thuật, tên class và đường dẫn route', () => {
    const ai: UiTreeNodeDto[] = [{ ...tree[0], children: [{ ...tree[0].children![0], children: [el('x', { aiLabel: 'Xuất báo cáo', displayName: 'Export' })] }] }];
    expect(searchUi(ai, 'xuat bao cao').elements.map((h) => h.node.nodeKey)).toEqual(['x']);
    expect(searchUi(tree, 'onrefresh').elements.map((h) => h.node.nodeKey)).toEqual(['icon']);
    expect(searchUi(tree, '/categories').screens.map((h) => h.node.nodeKey)).toEqual(['scr']);
    expect(searchUi(tree, 'CategoryList').screens.map((h) => h.node.nodeKey)).toEqual(['scr']);
  });

  it('tìm tên màn hình chỉ trả màn hình, không kéo theo phần tử bên trong; mỗi nút chỉ tính một lần', () => {
    const r = searchUi(tree, 'danh muc');
    expect(r.screens.map((h) => h.node.nodeKey)).toEqual(['scr', 'dlg']);
    expect(r.elements).toEqual([]);
    expect(searchUi(tree, 'them').elementTotal).toBe(1); // 'add' xuất hiện hai lần, tính một
  });

  it('cắt theo giới hạn nhưng vẫn báo tổng', () => {
    const many: UiTreeNodeDto[] = [{ ...tree[0], children: [{ ...tree[0].children![0], children: [el('a', { displayName: 'Lưu 1' }), el('b', { displayName: 'Lưu 2' }), el('c', { displayName: 'Lưu 3' })] }] }];
    const r = searchUi(many, 'luu', 2);
    expect(r.elements).toHaveLength(2);
    expect(r.elementTotal).toBe(3);
  });

  it('tô sáng không phân biệt dấu nhưng giữ nguyên chữ gốc', () => {
    expect(highlightFold('Thêm mới danh mục', 'them muc')).toEqual([
      { text: 'Thêm', hit: true },
      { text: ' mới danh ', hit: false },
      { text: 'mục', hit: true }
    ]);
    expect(highlightFold('Xóa', '')).toEqual([{ text: 'Xóa', hit: false }]);
  });

  it('findTrail trả đường tới lần xuất hiện đầu tiên', () => {
    expect(findTrail(tree, 'add')!.map((n) => n.nodeKey)).toEqual(['app', 'scr', 'add']);
    expect(findTrail(tree, 'zzz')).toBeNull();
  });

  it('đếm phần tử khác nhau trong nút chứa', () => {
    expect(countElements(tree[0])).toBe(4); // add xuất hiện hai lần nhưng chỉ tính một
  });
});

describe('trải phẳng cây theo trạng thái mở', () => {
  it('chỉ đi vào nút đang mở; cùng một nút ở hai nơi là hai dòng khác nhau', () => {
    const closed = flattenRows(tree, new Set(), false);
    expect(closed.map((r) => r.node.nodeKey)).toEqual(['app']);

    const open = flattenRows(tree, new Set(['>app', '>app>scr', '>app>scr>dlg']), false);
    expect(open.map((r) => r.node.nodeKey)).toEqual(['app', 'scr', 'add', 'del', 'icon', 'dlg', 'code', 'add']);
    const adds = open.filter((r) => r.node.nodeKey === 'add');
    expect(adds[0].id).not.toBe(adds[1].id);
    expect(adds[1].depth).toBe(3);
    expect(adds[1].trail.map((n) => n.nodeKey)).toEqual(['app', 'scr', 'dlg']);
  });

  it('openAll mở mọi nút (dùng khi đang lọc)', () => {
    expect(flattenRows(tree, new Set(), true)).toHaveLength(8);
  });

  it('revealIds cho mã dòng và các tổ tiên cần mở', () => {
    expect(revealIds(tree, 'code')).toEqual({ rowId: '>app>scr>dlg>code', ancestors: ['>app', '>app>scr', '>app>scr>dlg'] });
    expect(revealIds(tree, 'zzz')).toBeNull();
  });
});

describe('chế độ mặc định: chỉ phần tử gọi API', () => {
  const full: UiTreeNodeDto[] = [
    {
      nodeKey: 'app',
      kind: 'UI_APP',
      name: 'app',
      children: [
        {
          nodeKey: 'listing',
          kind: 'UI_SCREEN',
          name: 'Listing',
          callsApi: true,
          children: [el('toggle'), { nodeKey: 'form', kind: 'UI_FORM', name: 'f', children: [el('field', { elementType: 'input' })] }]
        },
        {
          nodeKey: 'edit',
          kind: 'UI_SCREEN',
          name: 'Edit',
          callsApi: false,
          children: [
            el('open'),
            {
              nodeKey: 'dlg',
              kind: 'UI_DIALOG',
              name: 'Dlg',
              callsApi: false,
              children: [{ nodeKey: 'f2', kind: 'UI_FORM', name: 'f2', children: [el('save', { callsApi: true }), el('cancel')] }]
            },
            { nodeKey: 'info', kind: 'UI_DIALOG', name: 'Info', callsApi: false, children: [el('close')] },
            { nodeKey: 'boot', kind: 'UI_DIALOG', name: 'Boot', callsApi: true, children: [el('ok')] }
          ]
        },
        { nodeKey: 'static', kind: 'UI_SCREEN', name: 'Static', callsApi: false, children: [el('link')] }
      ]
    }
  ];

  it('giữ ứng dụng và mọi màn hình kể cả màn hình không có phần tử gọi API', () => {
    const out = pruneToApi(full);
    expect(out[0].children!.map((s) => s.nodeKey)).toEqual(['listing', 'edit', 'static']);
    expect(out[0].children![2].children).toEqual([]);
    expect(out[0].children![0].children).toEqual([]); // màn hình tải dữ liệu khi mở nhưng không có phần tử gọi API
  });

  it('giữ hộp thoại / form chứa phần tử gọi API cùng tổ tiên; ẩn hộp thoại chỉ thao tác giao diện', () => {
    const edit = pruneToApi(full)[0].children![1];
    expect(edit.children!.map((c) => c.nodeKey)).toEqual(['dlg', 'boot']);
    const form = edit.children![0].children![0];
    expect(form.children!.map((c) => c.nodeKey)).toEqual(['save']);
  });

  it('hộp thoại tự gọi API khi mở được giữ dù không có phần tử gọi API bên trong', () => {
    const edit = pruneToApi(full)[0].children![1];
    expect(edit.children![1].nodeKey).toBe('boot');
    expect(edit.children![1].children).toEqual([]);
  });

  it('không sửa cây gốc', () => {
    pruneToApi(full);
    expect(full[0].children![1].children).toHaveLength(4);
  });

  it('đếm phần tử khác nhau gọi API / chỉ giao diện', () => {
    expect(countByApi(full)).toEqual({ api: 1, gateway: 0, uiOnly: 7 });
  });

  it('nút mở hộp thoại / chuyển trang có API (leadsToApi) được giữ, cùng hộp thoại đích', () => {
    const tree: UiTreeNodeDto[] = [
      {
        nodeKey: 'app',
        kind: 'UI_APP',
        name: 'app',
        children: [
          {
            nodeKey: 'scr',
            kind: 'UI_SCREEN',
            name: 'S',
            children: [
              el('add', { leadsToApi: true }),
              el('info', { leadsToApi: false }),
              { nodeKey: 'dlg', kind: 'UI_DIALOG', name: 'D', children: [el('save', { callsApi: true }), el('cancel')] }
            ]
          }
        ]
      }
    ];
    const screen = pruneToApi(tree)[0].children![0];
    expect(screen.children!.map((c) => c.nodeKey)).toEqual(['add', 'dlg']);
    expect(screen.children![1].children!.map((c) => c.nodeKey)).toEqual(['save']);
    expect(countByApi(tree)).toEqual({ api: 1, gateway: 1, uiOnly: 2 });
    expect(isApiStep(tree[0].children![0].children![0])).toBe(true);
  });
});
