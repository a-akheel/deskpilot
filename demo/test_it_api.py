import importlib.util, os, tempfile, threading, unittest, urllib.request, urllib.error, json
from pathlib import Path
class FixtureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory(); os.environ["IT_DB"]=cls.tmp.name+"/test.db"; os.environ["IT_API_TOKEN"]="test-token"
        spec=importlib.util.spec_from_file_location("fixture",Path(__file__).with_name("it_api.py")); module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        cls.server=module.ThreadingHTTPServer(("127.0.0.1",0),module.Handler)
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True); cls.thread.start()
        cls.base="http://127.0.0.1:"+str(cls.server.server_port)
    @classmethod
    def tearDownClass(cls): cls.server.shutdown(); cls.server.server_close(); cls.tmp.cleanup()
    def request(self,path,token="test-token",key=None,post=False):
        headers={"Authorization":"Bearer "+token}
        if key: headers["Idempotency-Key"]=key
        req=urllib.request.Request(self.base+path,b"{}" if post else None,headers)
        try:
            with urllib.request.urlopen(req) as response: return response.status,json.load(response)
        except urllib.error.HTTPError as exc:
            with exc: return exc.code,json.load(exc)
    def test_auth(self): self.assertEqual(403,self.request("/services/vpn",token="wrong")[0])
    def test_scope(self): self.assertEqual(404,self.request("/services/payroll")[0])
    def test_evidence(self): self.assertEqual("degraded",self.request("/services/vpn")[1]["status"])
    def test_key_required(self): self.assertEqual(400,self.request("/services/vpn/restart",post=True)[0])
    def test_idempotency(self):
        first=self.request("/services/vpn/restart",key="repeat",post=True)
        self.assertEqual(first,self.request("/services/vpn/restart",key="repeat",post=True))
        self.assertEqual(409,self.request("/services/email/restart",key="repeat",post=True)[0])
if __name__=="__main__": unittest.main()
