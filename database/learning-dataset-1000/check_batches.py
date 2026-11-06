import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
req = urllib.request.Request('http://127.0.0.1:8080/api/auth/login', headers={'Content-Type': 'application/json'}, data=b'{"username":"admin","password":"123456"}')
with urllib.request.urlopen(req) as resp:
    tok = json.loads(resp.read())['accessToken']
req2 = urllib.request.Request('http://127.0.0.1:8080/api/inventory/batches', headers={'Authorization': 'Bearer ' + tok})
with urllib.request.urlopen(req2) as resp:
    batches = json.loads(resp.read())
for b in batches:
    if b.get('expiryDate'):
        print(f"Batch {b['batchCode']:20} | Prod: {b['productId']} | Qty: {b['remainingQuantity']} | Expiry: {b.get('expiryDate')}")
