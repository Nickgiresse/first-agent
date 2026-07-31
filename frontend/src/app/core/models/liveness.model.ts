export type LivenessAction = 'BLINK' | 'TURN_LEFT' | 'TURN_RIGHT' | 'SMILE' | 'LOOK_UP' | 'LOOK_DOWN';

export interface ChallengeStartResponse {
  sessionId: string;
  actions: LivenessAction[];
  expiresInSeconds: number;
}

export interface ChallengeVerifyResponse {
  sessionId: string;
  action: LivenessAction;
  actionCompleted: boolean;
  completedActions: LivenessAction[];
  remainingActions: LivenessAction[];
  allActionsCompleted: boolean;
}

export interface ChallengeStatusResponse {
  sessionId: string;
  actions: LivenessAction[];
  completedActions: LivenessAction[];
  remainingActions: LivenessAction[];
  allActionsCompleted: boolean;
  live: boolean;
}

export const LIVENESS_ACTION_LABELS: Record<LivenessAction, string> = {
  BLINK: 'Clignez des yeux',
  TURN_LEFT: 'Tournez la tête à gauche',
  TURN_RIGHT: 'Tournez la tête à droite',
  SMILE: 'Souriez',
  LOOK_UP: 'Levez légèrement la tête',
  LOOK_DOWN: 'Baissez légèrement la tête'
};
