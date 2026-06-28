import os
from dataclasses import dataclass


@dataclass(frozen=True)
class AppConfig:
    bot_token: str
    database_url: str
    admins: set[int]

    donation_url: str
    java_host: str
    bedrock_host: str


def load_config() -> AppConfig:
    token = os.environ["BOT_TOKEN"]
    db = os.environ["DATABASE_URL"]

    raw = os.environ.get("ADMINS", "").strip()
    admins = {int(x.strip()) for x in raw.split(",") if x.strip().isdigit()}

    donation_url = os.environ.get("DONATION_URL", "").strip() or "https://www.donationalerts.com/r/makartaipan"
    java_host = os.environ.get("JAVA_HOST", "").strip() or "mc.astronexis.site"
    bedrock_host = os.environ.get("BEDROCK_HOST", "").strip() or "bedrock.astronexis.site"

    return AppConfig(
        bot_token=token,
        database_url=db,
        admins=admins,
        donation_url=donation_url,
        java_host=java_host,
        bedrock_host=bedrock_host,
    )