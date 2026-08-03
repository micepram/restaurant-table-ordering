"""Formatting helpers for demo.sh.

A file rather than inline `python3 -c` snippets: the shell's quoting rules and Python's
f-strings fight over the same quote characters, and the resulting escapes are unreadable
and easy to get subtly wrong.

Usage: python3 demo_helpers.py <command> [arg]   (JSON is read from stdin)
"""
import json
import sys


def load():
    try:
        return json.load(sys.stdin)
    except json.JSONDecodeError:
        return None


def money(cents):
    return f"{cents / 100:.2f}"


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else "field"
    arg = sys.argv[2] if len(sys.argv) > 2 else None
    data = load()

    if data is None:
        print("(no JSON response)")
        return

    if command == "field":
        print(data.get(arg, ""))

    elif command == "money":
        print(money(data.get(arg, 0)))

    elif command == "problem":
        print(f'   {data.get("status")}  {data.get("detail") or data.get("message") or data}')

    elif command == "ticket":
        matches = [t for t in data.get("tickets", []) if str(t["orderId"]) == str(arg)]
        if not matches:
            print("   ticket is not on the board")
            return
        t = matches[0]
        print(f'   #{t["orderId"]} {t["tableCode"]} {t["status"]} '
              f'{t["waitSeconds"]}s {t["urgency"]}')
        for line in t["lines"]:
            mods = f'  [{line["modifiers"]}]' if line["modifiers"] else ""
            note = f'  "{line["note"]}"' if line["note"] else ""
            print(f'     {line["quantity"]}x {line["name"]}{mods}{note}')

    elif command == "bill_totals":
        print(f'   subtotal {money(data["subtotalCents"])} '
              f'+ tip {money(data["tipCents"])} = {money(data["totalCents"])}')

    elif command == "split":
        total = 0
        for share in data["shares"]:
            share_total = share["amountCents"] + share["tipCents"]
            total += share_total
            print(f'   share {share["position"]}: {money(share["amountCents"])} '
                  f'+ {money(share["tipCents"])} tip = {money(share_total)}')
        verdict = "EXACT" if total == data["totalCents"] else "MISMATCH"
        print(f'   shares sum to {money(total)}, bill total {money(data["totalCents"])} -> {verdict}')

    elif command == "shares":
        # Space-separated for the shell to read into positional variables.
        print(" ".join(
            f'{s["amountCents"]} {s["tipCents"]}' for s in data["shares"]
        ))

    elif command == "bill":
        print(f'   bill #{data["orderId"]} {data["tableCode"]}: '
              f'{money(data["subtotalCents"])} + {money(data["tipCents"])} tip '
              f'= {money(data["totalCents"])}, outstanding {money(data["outstandingCents"])}, '
              f'settled={data["settled"]}')
        for payment in data["payments"]:
            reason = f'  ({payment["failureReason"]})' if payment["failureReason"] else ""
            print(f'     {payment["status"]:<8} '
                  f'{money(payment["amountCents"] + payment["tipCents"]):>8}  '
                  f'card ****{payment["cardLast4"]}{reason}')

    elif command == "attention":
        print(f'   flagged = {data["attentionFlagged"]} ({data.get("attentionNote")})')

    elif command == "item":
        for category in data["categories"]:
            for item in category["items"]:
                if str(item["id"]) == str(arg):
                    state = "AVAILABLE" if item["available"] else "86'd"
                    print(f'   {item["name"]} {money(item["priceCents"])}  {state}')

    else:
        print(f"unknown command: {command}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
