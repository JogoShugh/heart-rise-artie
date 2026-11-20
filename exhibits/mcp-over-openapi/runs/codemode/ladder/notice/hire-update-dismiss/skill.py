import api

def _ok(r, codes=(200, 201, 204)):
    if r['status'] not in codes:
        raise Exception(r)
    return r

def run(first_name, last_name, role, new_role, dismiss_name):
    h = _ok(api.hire({"firstName": first_name, "lastName": last_name, "role": role}))
    eid = h['body']['id']
    e = _ok(api.getEmployee(eid))
    _ok(api.update(eid, {"firstName": first_name, "lastName": last_name, "role": new_role}, if_match=e['etag']))
    s = _ok(api.searchEmployees(name=dismiss_name))
    if s['body']['count'] != 1:
        raise Exception(s)
    bid = s['body']['items'][0]['id']
    b = _ok(api.getEmployee(bid))
    if b['body']['status'] == 'working':
        _ok(api.giveNotice(bid, {}, if_match=b['etag']))
        b = _ok(api.getEmployee(bid))
    if b['body']['status'] != 'on notice':
        raise Exception(b)
    _ok(api.dismiss(bid, if_match=b['etag']))
    return "done"
