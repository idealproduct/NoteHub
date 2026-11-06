"""Run the isolated GPS provider proof with reversible emulator permissions.

The synthetic provider is private to this test and never replaces the system GPS.
It cannot be used as evidence of real hardware GPS accuracy.
"""
import argparse,json,pathlib,re,subprocess,sys
p=argparse.ArgumentParser()
p.add_argument('--adb',required=True); p.add_argument('--serial',required=True)
p.add_argument('--log',default='feature13-gps-device.log')
a=p.parse_args(); prefix=[a.adb,'-s',a.serial]; package='com.nqutaoitfteikc.notehub'
backup=pathlib.Path('dist/.location-test-permissions.json')
if backup.exists(): raise RuntimeError('Previous location permission backup exists; restore before retrying')
def shell(*args): return subprocess.check_output(prefix+['shell',*args],text=True,encoding='utf-8',errors='replace')
def permissions():
 text=shell('dumpsys','package',package)
 return {key:re.search(re.escape(key)+r': granted=(true|false)',text).group(1)=='true' for key in ['android.permission.ACCESS_COARSE_LOCATION','android.permission.ACCESS_FINE_LOCATION']}
original=permissions()
ops=shell('appops','get',package,'MOCK_LOCATION')
match=re.search(r'MOCK_LOCATION:\s+(allow|deny|ignore|foreground|default)',ops)
mode=match.group(1) if match else 'default'
backup.write_text(json.dumps({'permissions':original,'mock_location_mode':mode}),encoding='utf-8')
result=None
try:
 for key in original: shell('pm','grant',package,key)
 shell('appops','set',package,'MOCK_LOCATION','allow')
 result=subprocess.run([sys.executable,'scripts/Test-RegressionSafely.py','--adb',a.adb,'--serial',a.serial,'--classes',package+'.PhotoGpsDeviceTest','--log',a.log]).returncode
finally:
 shell('am','force-stop',package)
 for key,granted in reversed(list(original.items())):
  shell('pm','grant' if granted else 'revoke',package,key)
 shell('appops','set',package,'MOCK_LOCATION',mode)
 if permissions()!=original: raise RuntimeError('Permission restoration failed; keep backup')
 backup.unlink()
 print('Original location permissions and mock-location mode restored.')
 if result==0: shell('am','start','-W','-n',package+'/.MainActivity')
if result!=0: raise SystemExit(result or 1)
