import api

def _ck(r, code=200):
    if r['status'] != code:
        raise Exception(r)
    return r

def run(first, last, role, new_role, dismiss_name):
    h = _ck(api.hire({"firstName": first, "lastName": last, "role": role}), 201)
    eid = h['body']['id']
    g = _ck(api.getEmployee(eid))
    _ck(api.update(eid, {"firstName": first, "lastName": last, "role": new_role}, if_match=g['etag']))
    s = _ck(api.searchEmployees(name=dismiss_name))
    items = [i for i in s['body']['items'] if i['title'] == dismiss_name]
    if len(items) != 1:
        raise Exception(f"expected one match for {dismiss_name}: {s['body']}")
    bid = items[0]['id']
    b = _ck(api.getEmployee(bid))
    d = _ck(api.dismiss(bid, if_match=b['etag']))
    return {"hired": eid, "dismissed": d['body']}
