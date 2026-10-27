"""Rebuild the bundled SC font from Noto Sans CJK 2.004, with fontTools 4.67.0."""
import argparse,hashlib
from pathlib import Path
from fontTools import subset
from fontTools.ttLib import TTFont
RANGES='0020-007E,00A0-0103,0110-0113,011A-011B,0128-012B,0143-0144,0147-0148,014C-014F,0152-0153,0168-016D,0192,01A0-01A1,01AF-01B0,01CD-01DC,01F8-01F9,2002-2003,2010-2016,2018-201A,201C-201E,2020-2022,2025-2027,2030,2032-2033,2035,2039-203C,2042,2047-2049,2051,3000-303F,3400-4DB5,4E00-9FEF,FF01-FFBE,FFC2-FFC7,FFCA-FFCF,FFD2-FFD7,FFDA-FFDC,FFE0-FFE6,FFE8-FFEE'
RANGES+=',2190-21FF,2200-22FF,2300-23FF,2500-25FF,2600-26FF,2700-27BF'
def main():
 p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--face',type=int,default=2);p.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'runtime/src/main/resources/builtin/ui-font.otf');args=p.parse_args()
 f=TTFont(args.source,fontNumber=args.face,recalcTimestamp=False)
 if f['name'].getDebugName(1)!='Noto Sans CJK SC' or not f['name'].getDebugName(5).startswith('Version 2.004;'):raise ValueError('Expected Noto Sans CJK SC 2.004')
 codes=set()
 for part in RANGES.split(','):
  r=[int(s,16) for s in part.split('-')];codes.update(range(r[0],r[-1]+1))
 opts=subset.Options();opts.layout_features=['*'];opts.recalc_timestamp=False
 s=subset.Subsetter(options=opts);s.populate(unicodes=codes);s.subset(f);f.save(args.output)
 print('source SHA256',hashlib.sha256(args.source.read_bytes()).hexdigest());print('output SHA256',hashlib.sha256(args.output.read_bytes()).hexdigest())
if __name__=='__main__':main()
