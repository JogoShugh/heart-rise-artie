import api

def _ok(r, codes):
    if r['status'] not in codes:
        raise Exception(f"unexpected {r}")
    return r

def run(first="Samwise", last="Gamgee", role="gardener", new_role="gardener, mayor", dismiss_name="Bilbo Baggins"):
    h = _ok(api.hire({"firstName": first, "lastName": last, "role": role}), (200, 201))
    eid = h['body']['id']
    g = _ok(api.getEmployee(eid), (200,))
    _ok(api.update(eid, {"firstName": first, "lastName": last, "role": new_role}, if_match=g['etag']), (200,))
    s = _ok(api.searchEmployees(name=dismiss_name), (200,))
    items = s['body']['items']
    if len(items) != 1:
        raise Exception(f"expected one match for {dismiss_name}: {items}")
    bid = items[0]['id']
    b = _ok(api.getEmployee(bid), (200,))
    d = _ok(api.dismiss(bid, if_match=b['etag']), (200,))
    return d['body']