"""Build the existing Jingting WebView wrapper with the existing Android tools.

No dependencies beyond Python's standard library, JDK and Android build tools.
Set JINGTING_STORE_PASS (and optionally JINGTING_KEY_PASS) before signing.
The keystore is always supplied separately and is never included in source ZIPs.
"""
from pathlib import Path
import argparse, os, shutil, struct, subprocess, zipfile, hashlib, zlib

def run(args):
    result = subprocess.run([str(x) for x in args], capture_output=True)
    output = (result.stdout + result.stderr).decode('utf-8', errors='replace')
    if result.returncode:
        raise RuntimeError(f'{Path(args[0]).name} failed ({result.returncode}):\n{output}')
    return output

def read_zip(path):
    # Some older Windows aapt builds crash after writing valid local records.
    # Only salvage complete records whose uncompressed length and CRC both match.
    try:
        with zipfile.ZipFile(path) as archive:
            assert archive.testzip() is None
            return {i.filename: archive.read(i) for i in archive.infolist()}
    except zipfile.BadZipFile:
        data = path.read_bytes(); pos = 0; entries = {}
        while data[pos:pos+4] == b'PK\x03\x04':
            fields = struct.unpack_from('<IHHHHHIIIHH',data,pos)
            _,_,flags,method,_,_,crc,packed,size,nlen,xlen = fields
            if flags & 8: raise ValueError('Cannot salvage a data-descriptor ZIP')
            name = data[pos+30:pos+30+nlen].decode('utf-8')
            start = pos+30+nlen+xlen; raw = data[start:start+packed]
            value = zlib.decompress(raw,-15) if method == 8 else raw
            if method not in (0,8) or len(value) != size or zlib.crc32(value) != crc:
                raise ValueError('Invalid resource entry: '+name)
            entries[name] = value; pos = start+packed
        if not {'AndroidManifest.xml','resources.arsc'} <= entries.keys():
            raise ValueError('Incomplete Android resources')
        return entries

def aligned_zip(path, entries):
    with zipfile.ZipFile(path,'w') as archive:
        for name, raw in entries.items():
            info = zipfile.ZipInfo(name,(2026,11,13,0,0,0))
            info.compress_type = zipfile.ZIP_STORED if name.endswith(('.arsc','.dex')) else zipfile.ZIP_DEFLATED
            if info.compress_type == zipfile.ZIP_STORED:
                end = archive.fp.tell()+30+len(name.encode('utf-8'))
                padding = (-end) % 4
                if padding:
                    info.extra = struct.pack('<HH',0xD935,padding) + bytes(padding)
            archive.writestr(info,raw)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source',type=Path,required=True)
    parser.add_argument('--tools',type=Path,required=True)
    parser.add_argument('--keystore',type=Path)
    parser.add_argument('--unsigned',action='store_true',help='Compile an unsigned APK without a keystore')
    parser.add_argument('--build',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--alias',default='jingting-test')
    args=parser.parse_args()
    if not args.unsigned and (not args.keystore or 'JINGTING_STORE_PASS' not in os.environ): parser.error('Supply --keystore and set JINGTING_STORE_PASS, or use --unsigned')
    src=args.source.resolve(); dest=args.build.resolve(); dest.mkdir(parents=True,exist_ok=True)
    tools=args.tools.resolve(); sdk=tools/'build/android-15'; jdk=tools/'jdk/jdk-17.0.21+9/bin'
    android=tools/'platform/android-35/android.jar'
    gen=dest/'gen'; classes=dest/'classes'; dex=dest/'dex'
    for folder in (gen,classes,dex): folder.mkdir(exist_ok=True)
    resources=dest/'resources.apk'
    resources.unlink(missing_ok=True)
    # The Java package remains com.jingting.app for both application IDs.
    cmd=[sdk/'aapt.exe','package','-f','-M',src/'AndroidManifest.xml','-S',src/'res','-A',src/'assets','-I',android,'-F',resources,'-J',gen,'--custom-package','com.jingting.app','--min-sdk-version','24','--target-sdk-version','28']
    try: run(cmd)
    except RuntimeError:
        if not resources.exists(): raise
        print('aapt returned an error; checking every generated resource by CRC before repacking.')
    entries=read_zip(resources)
    java=list((src/'src').rglob('*.java'))+list(gen.rglob('*.java'))
    run([jdk/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-bootclasspath',str(sdk/'core-lambda-stubs.jar')+os.pathsep+str(android),'-d',classes,*java])
    run([jdk/'java.exe','-cp',sdk/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','24','--output',dex,*classes.rglob('*.class')])
    entries['classes.dex']=(dex/'classes.dex').read_bytes()
    assert entries['assets/index.html'] == (src/'assets/index.html').read_bytes()
    unsigned=dest/'unsigned.apk'; aligned_zip(unsigned,entries)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    if args.unsigned:
        shutil.copy2(unsigned,args.output)
        print('Unsigned APK compiled successfully.')
        return
    sign=[jdk/'java.exe','-jar',sdk/'lib/apksigner.jar']
    run(sign+['sign','--ks',args.keystore,'--ks-key-alias',args.alias,'--ks-pass','env:JINGTING_STORE_PASS','--key-pass','env:'+('JINGTING_KEY_PASS' if 'JINGTING_KEY_PASS' in os.environ else 'JINGTING_STORE_PASS'),'--out',args.output,unsigned])
    print(run(sign+['verify','--verbose','--print-certs',args.output]))
    print(run([sdk/'aapt.exe','dump','badging',args.output]).splitlines()[0])
    with zipfile.ZipFile(args.output) as archive:
        assert archive.testzip() is None
        for info in archive.infolist():
            if info.compress_type != zipfile.ZIP_STORED: continue
            with args.output.open('rb') as f:
                f.seek(info.header_offset); header=f.read(30)
                nlen,xlen=struct.unpack_from('<HH',header,26)
                assert (info.header_offset+30+nlen+xlen)%4 == 0, info.filename
    print('CRC and four-byte alignment verified.')
    print('APK SHA256:',hashlib.sha256(args.output.read_bytes()).hexdigest())

if __name__=='__main__': main()
