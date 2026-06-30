public class GridCell {
    public double tension;
    public boolean stormActive;
    public int stateOrdinal;

    public GridCell(double tension) {
        this.tension = tension;
        this.stormActive = false;
        this.stateOrdinal = 0;
    }

    public void updateState(ParameterSet p) {
        if (tension <= 0.0) {
            stateOrdinal = 0;
        } else if (tension >= p.t3) {
            stateOrdinal = 3;
        } else if (tension >= p.t2) {
            stateOrdinal = 2;
        } else if (tension >= p.t1) {
            stateOrdinal = 1;
        } else {
            stateOrdinal = 0;
        }
    }
}
