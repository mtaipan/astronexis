from app.db.repo.users_repo import UsersRepo
from app.db.repo.tickets_repo import TicketsRepo
from app.db.repo.ticket_messages_repo import TicketMessagesRepo

class TicketsService:
    def __init__(self):
        self.users = UsersRepo()
        self.tickets = TicketsRepo()
        self.msgs = TicketMessagesRepo()

    async def create_ticket(self, con, tg_id: int, category: str, subject: str, first_message: str) -> int:
        await self.users.ensure_user(con, tg_id)
        ticket_id = await self.tickets.create(con, tg_id, category, subject)
        await self.msgs.add(con, ticket_id, tg_id, first_message)
        return ticket_id