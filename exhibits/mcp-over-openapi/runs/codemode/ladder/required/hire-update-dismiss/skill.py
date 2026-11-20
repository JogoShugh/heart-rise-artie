import api

def ok(r, codes=(200, 201, 204)):
    if r['status'] not in codes:
        raise Exception(r)
    return r

def run(first, last, role, new_role, dismiss_name, reason="role change"):
    h = ok(api.hire({"firstName": first, "lastName": last, "role": role}))
    eid = h['body']['id']
    g = ok(api.getEmployee(eid))
    ok(api.update(eid, {"firstName": first, "lastName": last, "role": new_role, "reason": reason}, if_match=g['etag']))
    s = ok(api.searchEmployees(name=dismiss_name))
    items = s['body']['items']
    if len(items) != 1:
        raise Exception("expected one match for %s: %s" % (dismiss_name, items))
    did = items[0]['id']
    d = ok(api.getEmployee(did))
    ok(api.dismiss(did, if_match=d['etag']))
    return "done"
