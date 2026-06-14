export interface CareerGoal {
  id: number;
  targetJob: string | null;
  targetPeriod: string | null;
  prioritySkill: string | null;
  preferredCompanyType: string | null;
  updatedAt: string;
}

export interface LearningPlanTask {
  id: number;
  learningPlanId: number;
  task: string;
  done: boolean;
  sortOrder: number;
  completedAt: string | null;
}

export interface LearningPlan {
  id: number;
  title: string;
  targetSkill: string;
  startDate: string | null;
  endDate: string | null;
  status: string;
  completionRate: number;
  tasks: LearningPlanTask[];
}

export interface CareerPlan {
  goal: CareerGoal | null;
  learningPlans: LearningPlan[];
}
