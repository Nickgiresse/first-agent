from pydantic import BaseModel, Field


class ChallengeStartResponse(BaseModel):
    sessionId: str
    actions: list[str]
    expiresInSeconds: int


class ChallengeVerifyResponse(BaseModel):
    sessionId: str
    action: str
    actionCompleted: bool
    completedActions: list[str]
    remainingActions: list[str]
    allActionsCompleted: bool


class ChallengeStatusResponse(BaseModel):
    sessionId: str
    actions: list[str]
    completedActions: list[str]
    remainingActions: list[str]
    allActionsCompleted: bool
    decision: str = Field(..., description="LIVE | NOT_LIVE | IN_PROGRESS")
