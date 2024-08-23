import calendar
import logging
import pytz
from datetime import date, datetime, timedelta

import pytz
from pytz import timezone

logger = logging.getLogger(__name__)


def get_first_and_last_of_month() -> tuple[datetime, datetime]:
    """Returns tuple with first and last date of current month"""
    utc_now = datetime.now(pytz.utc)
    last_day_of_month = calendar.monthrange(year=utc_now.year, month=utc_now.month)[1]

    return utc_now.replace(day=1), utc_now.replace(day=last_day_of_month)


def date_factory(days: int = 0, timezone=pytz.utc):
    """Return a date object with a given offset.

    Calculate a date based off of utc now with an offset equal to the number of days provided. Positive
    numbers for future dates, negative for past dates.

    Args:
        days (int, optional): Number of days to use as the offset. Defaults to 0.
        timezone (pytz.UTC): A pytz formatted timezone object. Defaults to pytz.utc.
    Returns:
        (date): A date object formatted as YYYY-MM-DD
    """
    new_date = datetime.now(timezone) + timedelta(days=days)
    return date(year=new_date.year, month=new_date.month, day=new_date.day)


def datetime_factory(
        days: int = 0, hours: int = 0, minutes: int = 0, seconds: int = 0, milliseconds: int = 0, timezone=pytz.utc
    ):
    """Return a datetime object with a given offset.

    Calculate a datetime based off of utc now with an offset equal to the number of days and/or minutes
    provided. Positive numbers for future times, negative for past times.

    Args:
        days (int, optional): Number of days to use as the offset. Defaults to 0.
        minutes (int, optional): Number of minutes to use as the offset. Defaults to 0.
        timezone (pytz.UTC): A pytz formatted timezone object. Defaults to pytz.utc.
    Returns:
        (datetime): A datetime object formatted as YYYY-MM-DD HH:MM:SS
    """
    delta = timedelta(days=days, hours=hours, minutes=minutes, milliseconds=milliseconds, seconds=seconds)
    new_datetime = datetime.now(timezone) + delta
    return datetime(
        year=new_datetime.year,
        month=new_datetime.month,
        day=new_datetime.day,
        hour=new_datetime.hour,
        minute=new_datetime.minute,
        second=new_datetime.second,
        tzinfo=timezone,
    )


def epoch_in_microseconds(timestamp=None, days=0, hours=0, minutes=0, seconds=0, milliseconds=0):
    now = timestamp or datetime.now(pytz.utc)
    time_delta = timedelta(days=days, hours=hours, minutes=minutes, seconds=seconds, milliseconds=milliseconds)
    adjusted_time = now + time_delta
    epoch_microseconds = int(adjusted_time.timestamp()) * 1000
    return epoch_microseconds


def epoch_in_seconds(timestamp=None, days=0, hours=0, minutes=0, seconds=0, milliseconds=0):
    now = timestamp or datetime.now(pytz.utc)
    time_delta = timedelta(days=days, hours=hours, minutes=minutes, seconds=seconds, milliseconds=milliseconds)
    adjusted_time = now + time_delta
    epoch_seconds = int(adjusted_time.timestamp())
    return epoch_seconds


def get_yesterday_datetime(time_zone: str, return_utc: bool = True) -> datetime:
    """Return yesterday of now datetime

    Used to get the previous day datetime. All database datetime fields that are
    not timezone aware are stored in UTC time. DCO converts to local advertiser time
    before doing calculations so any datetime manipulations have to be done in local
    time and then converted to UTC to be inserted into the database for testing.

    Args:
        time_zone (str): local time zone typically from the advertiser.time_zone
        return_utc (bool): Default - True. Determines if the returned datetime
            is converted back to UTC prior to return. Set false if additional
            manipulation to the time is needed before inserting into db

    """

    local_time = datetime.now(timezone(time_zone))
    yesterday = local_time - timedelta(days=1)

    if return_utc:
        return yesterday.astimezone(pytz.utc)

    return yesterday
