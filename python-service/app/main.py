import sys

from fastapi import FastAPI

from app.api.v1.document_router import router as document_router
from app.api.v1.face_router import router as face_router
from app.api.v1.liveness_router import router as liveness_router
from app.api.v1.verification_router import router as verification_router
from app.config.settings import get_settings


def _sortir_du_mode_efficacite() -> None:
    """Retire le processus du bridage EcoQoS de Windows 11.

    Un service lancé en arrière-plan et sans fenêtre y est placé d'office, et
    l'inférence ONNX s'y exécute 4 à 8 fois plus lentement : mesuré ici, une
    CNI passait de quelques secondes à plus de 20. Sans effet hors Windows.
    """
    if sys.platform != "win32":
        return
    try:
        import ctypes

        class _Etat(ctypes.Structure):
            _fields_ = [("Version", ctypes.c_ulong),
                        ("ControlMask", ctypes.c_ulong),
                        ("StateMask", ctypes.c_ulong)]

        k32 = ctypes.windll.kernel32
        # Typage indispensable : sans restype/argtypes, le pseudo-handle (-1)
        # de GetCurrentProcess est tronqué en 32 bits et l'appel échoue
        # silencieusement sur ERROR_INVALID_HANDLE.
        k32.GetCurrentProcess.restype = ctypes.c_void_p
        k32.SetProcessInformation.argtypes = [
            ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p, ctypes.c_ulong]
        etat = _Etat(1, 0x1, 0)   # v1, EXECUTION_SPEED, jamais bridé
        k32.SetProcessInformation(k32.GetCurrentProcess(), 4,
                                  ctypes.byref(etat), ctypes.sizeof(etat))
    except Exception:
        pass


_sortir_du_mode_efficacite()

settings = get_settings()

app = FastAPI(
    title=settings.app_name,
    version="0.1.0",
    description="Microservice interne de vision par ordinateur (analyse documentaire, OCR, "
    "reconnaissance faciale, vivacité, comparaison biométrique). Appelé exclusivement par Spring Boot.",
)

app.include_router(document_router, prefix="/api/v1")
app.include_router(face_router, prefix="/api/v1")
app.include_router(liveness_router, prefix="/api/v1")
app.include_router(verification_router, prefix="/api/v1")


@app.get("/health", tags=["health"])
def health() -> dict[str, str]:
    return {"status": "ok"}
