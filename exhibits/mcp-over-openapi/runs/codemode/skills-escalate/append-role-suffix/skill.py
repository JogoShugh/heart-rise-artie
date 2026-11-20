import api
def run(name="Frodo Baggins", suffix=", retired"):
    s=api.searchEmployees(name=name)
    if s['status']!=200: raise Exception(s)
    m=[i for i in s['body']['items'] if i['title']==name]
    if len(m)!=1: raise Exception(("matches",s['body']))
    id=m[0]['id']
    for _ in range(3):
        g=api.getEmployee(id)
        if g['status']!=200: raise Exception(g)
        b=g['body']
        if b['role'].endswith(suffix): return b
        u=api.update(id,{"firstName":b['firstName'],"lastName":b['lastName'],"position":b['role']+suffix},if_match=g['etag'])
        if u['status']==200: return u['body']
        if u['status']!=412: raise Exception(u)
    raise Exception("retries exhausted")
