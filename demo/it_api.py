"""Loopback-only fixture API. Durable idempotency is enforced in SQLite."""
import json, os, sqlite3
from http.server import BaseHTTPRequestHandler, HTTPServer
DB = os.getenv("IT_DB", "demo.db")
TOKEN = os.environ["IT_API_TOKEN"]
with sqlite3.connect(DB) as db:
    db.execute("CREATE TABLE IF NOT EXISTS actions (key TEXT PRIMARY KEY, service TEXT, result TEXT)")
class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def reply(self, status, data):
        raw=json.dumps(data).encode(); self.send_response(status)
        self.send_header("Content-Type","application/json"); self.send_header("Content-Length",str(len(raw)))
        self.end_headers(); self.wfile.write(raw)
    def handle_api(self, write=False):
        if self.headers.get("Authorization") != "Bearer "+TOKEN:
            return self.reply(403,{"error":"forbidden"})
        parts=self.path.strip("/").split("/")
        if len(parts) != (3 if write else 2) or parts[0]!="services" or parts[1] not in {"vpn","email","helpdesk"} or (write and parts[2]!="restart"):
            return self.reply(404,{"error":"unknown tool"})
        service=parts[1]
        if not write: return self.reply(200,{"service":service,"status":"degraded" if service=="vpn" else "healthy","evidence":"fixture:monitor:"+service})
        key=self.headers.get("Idempotency-Key")
        if not key: return self.reply(400,{"error":"idempotency key required"})
        with sqlite3.connect(DB) as db:
            row=db.execute("SELECT service,result FROM actions WHERE key=?",(key,)).fetchone()
            if row and row[0]!=service: return self.reply(409,{"error":"key reused with different service"})
            result=row[1] if row else json.dumps({"service":service,"restarted":True,"simulated":True})
            if not row: db.execute("INSERT INTO actions VALUES (?,?,?)",(key,service,result))
        return self.reply(200,json.loads(result))
    def do_GET(self): self.handle_api()
    def do_POST(self): self.handle_api(True)
if __name__=="__main__": HTTPServer(("127.0.0.1",8091),Handler).serve_forever()
