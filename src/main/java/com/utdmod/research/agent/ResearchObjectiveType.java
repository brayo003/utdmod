package com.utdmod.research.agent;

/** High-level execution objectives issued by the server experiment scheduler. */
public enum ResearchObjectiveType {
  STOP(0),
  GO_TO(1),
  EXPLORE(2),
  MINE(3),
  BUILD_SHELTER(4),
  RETURN_HOME(5),
  FACE(6);

  private final int id;

  ResearchObjectiveType(int id) {
    this.id = id;
  }

  public int id() {
    return id;
  }

  public static ResearchObjectiveType fromId(int id) {
    for (ResearchObjectiveType t : values()) {
      if (t.id == id) return t;
    }
    return STOP;
  }
}
