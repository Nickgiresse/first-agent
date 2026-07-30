import random

ALL_ACTIONS = ["BLINK", "TURN_LEFT", "TURN_RIGHT", "SMILE", "LOOK_UP", "LOOK_DOWN"]


def generate_challenge(count: int) -> list[str]:
    return random.sample(ALL_ACTIONS, k=min(count, len(ALL_ACTIONS)))
