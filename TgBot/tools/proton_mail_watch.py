import os
import time
import imaplib
import email
from email.header import decode_header

import asyncio
from telegram import Bot


def _dec(s):
    if not s:
        return ""
    parts = decode_header(s)
    out = []
    for v, enc in parts:
        if isinstance(v, bytes):
            out.append(v.decode(enc or "utf-8", errors="replace"))
        else:
            out.append(v)
    return "".join(out)


async def notify_admins(bot: Bot, admins: list[int], text: str):
    for a in admins:
        try:
            await bot.send_message(chat_id=a, text=text)
        except Exception:
            pass


def main():
    # bridge IMAP settings (обычно 127.0.0.1:1143, но зависит от Bridge)
    imap_host = os.environ.get("PROTON_IMAP_HOST", "127.0.0.1")
    imap_port = int(os.environ.get("PROTON_IMAP_PORT", "1143"))
    imap_user = os.environ["PROTON_IMAP_USER"]
    imap_pass = os.environ["PROTON_IMAP_PASS"]

    bot_token = os.environ["BOT_TOKEN"]
    admins_raw = os.environ.get("ADMINS", "")
    admins = [int(x.strip()) for x in admins_raw.split(",") if x.strip().isdigit()]

    mailbox = os.environ.get("PROTON_IMAP_MAILBOX", "INBOX")

    bot = Bot(token=bot_token)

    last_seen_uid = None

    while True:
        try:
            M = imaplib.IMAP4(imap_host, imap_port)
            M.login(imap_user, imap_pass)
            M.select(mailbox)

            typ, data = M.uid("search", None, "UNSEEN")
            if typ != "OK":
                M.logout()
                time.sleep(10)
                continue

            uids = data[0].split()
            for uid in uids:
                if last_seen_uid == uid:
                    continue

                typ, msg_data = M.uid("fetch", uid, "(RFC822)")
                if typ != "OK":
                    continue

                raw = msg_data[0][1]
                msg = email.message_from_bytes(raw)

                subj = _dec(msg.get("Subject"))
                frm = _dec(msg.get("From"))

                # пытаемся взять немного текста
                snippet = ""
                if msg.is_multipart():
                    for part in msg.walk():
                        ctype = part.get_content_type()
                        if ctype == "text/plain":
                            payload = part.get_payload(decode=True) or b""
                            snippet = payload.decode(part.get_content_charset() or "utf-8", errors="replace")
                            break
                else:
                    payload = msg.get_payload(decode=True) or b""
                    snippet = payload.decode(msg.get_content_charset() or "utf-8", errors="replace")

                snippet = (snippet or "").strip().replace("\r", "")
                if len(snippet) > 300:
                    snippet = snippet[:300] + "…"

                text = (
                    "📧 Новое письмо на astronexis@proton.me\n\n"
                    f"От: {frm}\n"
                    f"Тема: {subj}\n\n"
                    f"{snippet}"
                )

                asyncio.run(notify_admins(bot, admins, text))
                last_seen_uid = uid

            M.logout()
        except Exception:
            # просто не падаем
            pass

        time.sleep(15)


if __name__ == "__main__":
    main()