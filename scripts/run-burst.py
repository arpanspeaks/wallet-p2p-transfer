import concurrent.futures
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://localhost:8080"
WALLET_RACE_REQUESTS = 50
IDEMPOTENCY_RETRIES = 30
CONTENTION_ATTEMPTS = 120
MAX_WORKERS = 50

def call(method, path, token, data=None):
    body = json.dumps(data).encode() if data else None
    request = urllib.request.Request(BASE + path, body, method=method,
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())

def parallel(items, task):
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(MAX_WORKERS, len(items))) as executor:
        return list(executor.map(task, items))

def wallet(token):
    status, body = call("POST", "/wallets", token)
    assert status == 201, body
    return body["wallet_id"]

race_user = "race-" + str(uuid.uuid4())
race = parallel(range(WALLET_RACE_REQUESTS), lambda _: wallet(race_user))
assert len(set(race)) == 1, "wallet race returned multiple IDs"
print(f"wallet race ({WALLET_RACE_REQUESTS} requests, up to {MAX_WORKERS} workers): PASS")

alice, bob, carol = ("alice-" + str(uuid.uuid4()), "bob-" + str(uuid.uuid4()), "carol-" + str(uuid.uuid4()))
a, b, c = wallet(alice), wallet(bob), wallet(carol)
key = "storm-" + str(uuid.uuid4())
body = {"from": a, "to": b, "amount_paise": 100, "idempotency_key": key}
storm = parallel(range(IDEMPOTENCY_RETRIES), lambda _: call("POST", "/transfers", alice, body))
assert all(status == 201 for status, _ in storm), storm
assert len({response["transfer_id"] for _, response in storm}) == 1, "idempotency storm returned different transfers"
print(f"idempotency storm ({IDEMPOTENCY_RETRIES} requests, up to {MAX_WORKERS} workers): PASS")

def balance(wallet_id, owner):
    status, response = call("GET", "/wallets/" + wallet_id, owner)
    assert status == 200, response
    return response["balance_paise"]

before = sum((balance(a, alice), balance(b, bob), balance(c, carol)))
pairs = ((a, b, alice), (b, a, bob), (a, c, alice), (c, a, carol))
requests = [(source, destination, token, str(uuid.uuid4())) for i in range(CONTENTION_ATTEMPTS) for source, destination, token in (pairs[i % len(pairs)],)]
results = parallel(requests, lambda item: call("POST", "/transfers", item[2], {
    "from": item[0], "to": item[1], "amount_paise": 2000, "idempotency_key": item[3]}))

decline_status, decline_response = call("POST", "/transfers", alice, {
    "from": a, "to": b, "amount_paise": 1_000_000, "idempotency_key": str(uuid.uuid4())})
assert decline_status == 422 and decline_response["status"] == "DECLINED", decline_response
after = [balance(a, alice), balance(b, bob), balance(c, carol)]
assert before == sum(after) and min(after) >= 0, "conservation/contention failed"
assert all(status in (201, 422) for status, _ in results), results
print(f"conservation under contention ({CONTENTION_ATTEMPTS} requests, up to {MAX_WORKERS} workers) and clean overdraft decline: PASS")
