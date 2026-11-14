
import api

def run(surname="Miller"):
    ids=[]; page=None
    while True:
        r=api.searchEmployees(name=surname, page=page) if page else api.searchEmployees(name=surname)
        if r["status"]!=200: raise Exception(r)
        ids+= [i["id"] for i in r["body"]["items"]]
        nxt=r["body"].get("next") or r["body"].get("nextPage")
        if not nxt or not r["body"]["items"]: break
        page=nxt if isinstance(nxt,int) else (page or 1)+1
    out=[]
    for i in ids:
        e=api.getEmployee(i)
        if e["status"]!=200: raise Exception(e)
        b=e["body"]
        if b["lastName"]!=surname: continue
        s=b["status"]
        if s=="working":
            r=api.giveNotice(i,{},if_match=e["etag"])
        elif s=="on notice":
            r=api.dismiss(i,if_match=e["etag"])
        elif s=="dismissed":
            out.append((i,s,"left")); continue
        else: raise Exception(e)
        if r["status"] not in (200,201,204): raise Exception(r)
        out.append((i,s,r["status"]))
    return out
