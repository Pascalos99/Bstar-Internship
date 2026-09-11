package gametree;

public class GenerationSettings {
	
	public static GenerationSettings berlinerSplit = new GenerationSettings(Style.BerlinerSplit, 1, true);
	public static GenerationSettings berliner = new GenerationSettings(Style.Berliner, 1, true);
	public static GenerationSettings palay = new GenerationSettings(Style.Palay, 1, true);
	public static GenerationSettings legacy = new GenerationSettings(Style.Legacy, 1, true);
	public static GenerationSettings basic = new GenerationSettings(Style.BaseRulesAfter, 1.6, false);
	
	public enum Style { Berliner, Palay, BaseRulesBefore, BaseRulesAfter, Legacy, BerlinerSplit };
	
	public final Style style;
	public final double growth;
	public final float force_value_in_parent_range_chance;
	public final boolean preserve_sign;
	
	/**
	 * @param style determines how to generate children bounds while expanding the tree.
	 * @param growth a number {@code > 0} that determines how quickly a tree converges.
	 * 			Lower values make the tree converge faster. Default value is {@code 1}.
	 * @param preserve_sign whether to ensure that <b>MAX</b> values are always positive
	 * 			and <b>MIN</b> values are always negative.
	 */
	public GenerationSettings(Style style, double growth, float force_chance, boolean preserve_sign) {
		this.style = style;
		this.growth = growth;
		this.force_value_in_parent_range_chance = force_chance;
		this.preserve_sign = preserve_sign;
	}
	public GenerationSettings(Style style, double growth, boolean preserve_sign) {
		this(style, growth, 0, preserve_sign);
	}
	
	@Override
	public String toString() {
		switch(style) {
		case Berliner:
		case Legacy:
		case Palay: return style.toString();
		default: return String.format("%s-%.1f%s", style, growth, preserve_sign ? "-PS":"");
		}
	}
	
}
