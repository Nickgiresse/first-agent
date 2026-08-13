import random

# Deux familles d'actions, et la distinction est ce qui fait tenir le défi.
#
# Une action DÉFORMANTE exige que le visage lui-même change : les paupières se
# ferment, la bouche s'étire. Un tirage papier ou un écran figé en est
# incapable.
#
# Une action ROTATIVE ne demande qu'un changement d'orientation. Elle se
# reproduit en faisant pivoter une photo imprimée devant la caméra.
ACTIONS_DEFORMANTES = ["BLINK", "SMILE"]
ACTIONS_ROTATIVES = ["TURN_LEFT", "TURN_RIGHT", "LOOK_UP", "LOOK_DOWN"]
ALL_ACTIONS = ACTIONS_DEFORMANTES + ACTIONS_ROTATIVES


def generate_challenge(count: int) -> list[str]:
    """Tire les actions du défi en garantissant au moins une action déformante.

    Le tirage précédent était un `random.sample` libre sur les six actions. Avec
    trois actions demandées, les combinaisons exclusivement rotatives sont
    C(4,3)=4 sur C(6,3)=20, soit **une session sur cinq** entièrement
    franchissable en faisant pivoter une photo imprimée. La vivacité ne prouvait
    alors rien, et rien dans les traces ne permettait de distinguer ces sessions
    des autres.

    On tire donc d'abord l'action déformante obligatoire, puis le reste parmi
    les actions restantes, et on mélange pour que sa position ne soit pas
    prévisible : un fraudeur qui saurait que la première action est toujours un
    clignement n'aurait à jouer le visage réel qu'au premier instant.
    """
    count = max(1, min(count, len(ALL_ACTIONS)))
    obligatoire = random.choice(ACTIONS_DEFORMANTES)
    autres = [action for action in ALL_ACTIONS if action != obligatoire]
    tirage = [obligatoire, *random.sample(autres, k=count - 1)]
    random.shuffle(tirage)
    return tirage
