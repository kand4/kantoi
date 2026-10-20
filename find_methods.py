import zipfile, struct, sys
sys.stdout.reconfigure(encoding='utf-8')

with zipfile.ZipFile('_base_backup/truecaller_original_base.apk', 'r') as z:
    dex = z.read('classes6.dex')

string_ids_size = struct.unpack_from('<I', dex, 0x34)[0]
string_ids_off = struct.unpack_from('<I', dex, 0x38)[0]
type_ids_size = struct.unpack_from('<I', dex, 0x40)[0]
type_ids_off = struct.unpack_from('<I', dex, 0x44)[0]
method_ids_size = struct.unpack_from('<I', dex, 0x58)[0]
method_ids_off = struct.unpack_from('<I', dex, 0x5c)[0]
class_defs_size = struct.unpack_from('<I', dex, 0x60)[0]
class_defs_off = struct.unpack_from('<I', dex, 0x64)[0]

def get_str(idx):
    off = struct.unpack_from('<I', dex, string_ids_off + idx * 4)[0]
    p = off
    l = 0; s = 0
    while True:
        b = dex[p]; p += 1
        l |= (b & 0x7f) << s
        if (b & 0x80) == 0: break
        s += 7
    return dex[p:p+l].decode('utf-8', errors='ignore')

def get_type(idx):
    s_idx = struct.unpack_from('<I', dex, type_ids_off + idx * 4)[0]
    return get_str(s_idx)

class Reader:
    def __init__(self, p):
        self.p = p
    def read_uleb(self):
        res = 0; shift = 0
        while True:
            b = dex[self.p]; self.p += 1
            res |= (b & 0x7f) << shift
            if (b & 0x80) == 0: break
            shift += 7
        return res

for target_off in [1964132, 4386660, 6789902]:
    print(f'Checking target_off: {target_off}')
    for i in range(class_defs_size):
        c_idx = struct.unpack_from('<I', dex, class_defs_off + i*32)[0]
        data_off = struct.unpack_from('<I', dex, class_defs_off + i*32 + 24)[0]
        if data_off == 0: continue
        r = Reader(data_off)
        static_fields = r.read_uleb()
        instance_fields = r.read_uleb()
        direct_methods = r.read_uleb()
        virtual_methods = r.read_uleb()
        for _ in range(static_fields): r.read_uleb(); r.read_uleb()
        for _ in range(instance_fields): r.read_uleb(); r.read_uleb()
        m_idx = 0
        for _ in range(direct_methods + virtual_methods):
            m_idx += r.read_uleb()
            flags = r.read_uleb()
            code_off = r.read_uleb()
            if code_off != 0:
                insns_size = struct.unpack_from('<I', dex, code_off + 12)[0]
                insns_start = code_off + 16
                insns_end = insns_start + insns_size * 2
                if insns_start <= target_off < insns_end:
                    class_type = get_type(c_idx)
                    _, _, name_idx = struct.unpack_from('<HHI', dex, method_ids_off + m_idx*8)
                    m_name = get_str(name_idx)
                    print(f'MATCH: {class_type} -> {m_name}() at offset {target_off}')
