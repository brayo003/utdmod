public class ArchitecturalAudit {
    public static void main(String[] args) {
        ParameterSet params = new ParameterSet();
        params.v2AdaptiveRelaxationEnabled = true;
        GridSimulationV2 sim = new GridSimulationV2(1, 1, 1, params);
        double a0 = 0.5;
        double m0 = 0.4;
        double b0 = 0.3;
        double c0 = 0.2;
        double ext0 = 0.1;
        double tr0 = 0.0;

        GridSimulationV2.CellUpdateResult baseResult = sim.computeCellUpdate(a0, m0, b0, c0, ext0, tr0, true);
        System.out.println("BASE nextActivation=" + baseResult.nextActivation + " nextMemory=" + baseResult.nextMemory + " nextBaseline=" + baseResult.nextBaseline + " nextDepth=" + baseResult.nextDepth);

        test("Activation", a0, m0, b0, c0, ext0, tr0, baseResult, 10.0, 100.0, 0.0, true, false, false, false, false, false);
        test("Baseline", a0, m0, b0, c0, ext0, tr0, baseResult, 10.0, 100.0, 0.0, false, false, true, false, false, false);
        test("Memory", a0, m0, b0, c0, ext0, tr0, baseResult, 10.0, 100.0, 0.0, false, true, false, false, false, false);
        test("FieldDepth", a0, m0, b0, c0, ext0, tr0, baseResult, 10.0, 100.0, 0.0, false, false, false, true, false, false);
        test("Transport", a0, m0, b0, c0, ext0, tr0, baseResult, 10.0, 100.0, 0.0, false, false, false, false, true, false);
        test("ExternalInjection", a0, m0, b0, c0, ext0, tr0, baseResult, 10.0, 100.0, 0.0, false, false, false, false, false, true);
    }

    private static void test(String label, double a0, double m0, double b0, double c0, double ext0, double tr0,
                             GridSimulationV2.CellUpdateResult baseResult,
                             double scale10, double scale100, double zero,
                             boolean affectActivation, boolean affectMemory, boolean affectBaseline, boolean affectFieldDepth,
                             boolean affectTransport, boolean affectExternal) {
        double a1 = affectActivation ? a0 * scale10 : a0;
        double m1 = affectMemory ? m0 * scale10 : m0;
        double b1 = affectBaseline ? b0 * scale10 : b0;
        double c1 = affectFieldDepth ? c0 * scale10 : c0;
        double ext1 = affectExternal ? ext0 * scale10 : ext0;
        double tr1 = affectTransport ? tr0 * scale10 : tr0;
        GridSimulationV2.CellUpdateResult r10 = new GridSimulationV2(1, 1, 1, new ParameterSet()).computeCellUpdate(a1, m1, b1, c1, ext1, tr1, true);

        double a2 = affectActivation ? a0 * scale100 : a0;
        double m2 = affectMemory ? m0 * scale100 : m0;
        double b2 = affectBaseline ? b0 * scale100 : b0;
        double c2 = affectFieldDepth ? c0 * scale100 : c0;
        double ext2 = affectExternal ? ext0 * scale100 : ext0;
        double tr2 = affectTransport ? tr0 * scale100 : tr0;
        GridSimulationV2.CellUpdateResult r100 = new GridSimulationV2(1, 1, 1, new ParameterSet()).computeCellUpdate(a2, m2, b2, c2, ext2, tr2, true);

        double a3 = affectActivation ? zero : a0;
        double m3 = affectMemory ? zero : m0;
        double b3 = affectBaseline ? zero : b0;
        double c3 = affectFieldDepth ? zero : c0;
        double ext3 = affectExternal ? zero : ext0;
        double tr3 = affectTransport ? zero : tr0;
        GridSimulationV2.CellUpdateResult r0 = new GridSimulationV2(1, 1, 1, new ParameterSet()).computeCellUpdate(a3, m3, b3, c3, ext3, tr3, true);

        boolean changed10 = Math.abs(r10.nextActivation - baseResult.nextActivation) > 1e-12 || Math.abs(r10.nextMemory - baseResult.nextMemory) > 1e-12 || Math.abs(r10.nextBaseline - baseResult.nextBaseline) > 1e-12 || Math.abs(r10.nextDepth - baseResult.nextDepth) > 1e-12;
        boolean changed100 = Math.abs(r100.nextActivation - baseResult.nextActivation) > 1e-12 || Math.abs(r100.nextMemory - baseResult.nextMemory) > 1e-12 || Math.abs(r100.nextBaseline - baseResult.nextBaseline) > 1e-12 || Math.abs(r100.nextDepth - baseResult.nextDepth) > 1e-12;
        boolean changed0 = Math.abs(r0.nextActivation - baseResult.nextActivation) > 1e-12 || Math.abs(r0.nextMemory - baseResult.nextMemory) > 1e-12 || Math.abs(r0.nextBaseline - baseResult.nextBaseline) > 1e-12 || Math.abs(r0.nextDepth - baseResult.nextDepth) > 1e-12;
        System.out.println(label + " -> 10x=" + changed10 + " 100x=" + changed100 + " zero=" + changed0);
    }
}
