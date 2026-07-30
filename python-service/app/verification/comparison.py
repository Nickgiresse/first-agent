import numpy as np


def cosine_similarity(embedding_a: np.ndarray, embedding_b: np.ndarray) -> float:
    norm_a = embedding_a / (np.linalg.norm(embedding_a) + 1e-10)
    norm_b = embedding_b / (np.linalg.norm(embedding_b) + 1e-10)
    return float(np.dot(norm_a, norm_b))
