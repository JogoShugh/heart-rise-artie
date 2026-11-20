import api

def _ok(r, codes=(200, 201)):
    if r['status'] not in codes:
        raise Exception(r)
    return r

def run(first, last, role, new_role, dismiss_name):
    h = _ok(api.hire({"firstName": first, "lastName": last, "position": role}))
    id = h['body']['id']
    g = _ok(api.getEmployee(id))
    _ok(api.update(id, {"firstName": first, "lastName": last, "position": new_role}, if_match=g['etag']))
    s = _ok(api.searchEmployees(name=dismiss_name))
    items = s['body']['items']
    if len(items) != 1:
        raise Exception(("expected exactly one match", items))
    did = items[0]['id']
    b = _ok(api.getEmployee(did))
    _ok(api.dismiss(did, if_match=b['etag']))
    return "done"
