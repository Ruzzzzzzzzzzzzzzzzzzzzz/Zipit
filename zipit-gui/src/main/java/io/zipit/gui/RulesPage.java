package io.zipit.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/** Framework presets, keep rules, public API policy and classpath libraries. */
final class RulesPage extends JPanel {

    private final ZipitFrame frame;
    private final GuiState state;
    private final List<PresetCard> presetCards = new ArrayList<PresetCard>();
    private final JLabel ruleCount = Ui.faint(I18n.t("rules.ruleCountPlural", 0));
    private final JLabel currentPreset = Ui.pill(I18n.t("ui.default.2"));

    RulesPage(ZipitFrame frame, GuiState state) {
        super(new BorderLayout());
        this.frame = frame;
        this.state = state;
        setOpaque(false);

        PageHeader header = new PageHeader(I18n.t("ui.configuration"), I18n.t("ui.rules.presets"),
                I18n.t("ui.framework.aware.keep.rules.public.api.policy.and.classpath.setup"));
        JButton dashboard = Ui.button(I18n.t("ui.dashboard"), "dashboard");
        dashboard.addActionListener(e -> frame.showPage(ZipitFrame.PAGE_DASHBOARD));
        JButton protect = Ui.primary(I18n.t("ui.protect.jar"), "play");
        protect.addActionListener(e -> frame.runNow());
        header.actions(dashboard, protect);
        add(header, BorderLayout.NORTH);

        JPanel body = Ui.pageBody();
        body.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(24), Theme.px(22),
                Theme.px(24)));

        addSectionHeader(body, "01", I18n.t("ui.presets"),
                I18n.t("ui.policy.bundles.that.add.framework.aware.keep.rules"));
        body.add(buildPresetGrid());
        body.add(Box.createVerticalStrut(Theme.px(10)));
        body.add(presetNote());
        body.add(Box.createVerticalStrut(Theme.px(14)));

        addSectionHeader(body, "02", I18n.t("ui.keep.rules"),
                I18n.t("ui.one.ant.style.glob.owner.member.or.annotation.per.line"));
        body.add(twoColumns(buildKeepCard(), buildPolicyColumn()));
        body.add(Box.createVerticalStrut(Theme.px(14)));

        addSectionHeader(body, "03", I18n.t("ui.soft.keep.classpath"),
                I18n.t("ui.preserve.names.without.freezing.the.class.and.add.library.jars"));
        body.add(twoColumns(buildSoftKeepCard(), buildLibrariesCard()));
        body.add(Box.createVerticalStrut(Theme.px(14)));

        addSectionHeader(body, "04", I18n.t("ui.mapping.policy"),
                I18n.t("ui.where.release.mappings.live"));
        body.add(buildMappingNotesCard());

        add(Ui.pageScroll(body), BorderLayout.CENTER);

        state.addChangeListener(new Runnable() {
            @Override
            public void run() {
                refreshPresets();
            }
        });
        refreshPresets();
        updateRuleCount();
        state.keep.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateRuleCount();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateRuleCount();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateRuleCount();
            }
        });
    }

    // ------------------------------------------------------------------
    // presets
    // ------------------------------------------------------------------

    private JPanel buildPresetGrid() {
        String[][] presets = {
                {GuiState.PRESET_NONE, I18n.t("ui.none.2"), I18n.t("ui.use.exactly.the.configured.pipeline"), "settings"},
                {"library", I18n.t("ui.library"), I18n.t("ui.soft.keep.public.api.and.keep"), "box"},
                {"spring-boot", I18n.t("ui.spring.boot"), I18n.t("ui.keep.controllers.services.and.config"), "zap"},
                {"jackson", I18n.t("ui.jackson"), I18n.t("ui.keep.jackson.jpa.jaxb.annotated.types"), "database"},
                {"service-loader", I18n.t("ui.service.loader"), I18n.t("ui.keep.names.valid.for.serviceloader"), "layers"},
                {"micronaut", I18n.t("ui.micronaut"), I18n.t("ui.keep.micronaut.beans.and.factories"), "cpu"},
                {"aggressive", I18n.t("ui.aggressive"), I18n.t("ui.max.coverage.flatten.wrap.and.junk"), "shield"},
        };
        JPanel grid = new JPanel(new GridLayout(0, 4, Theme.px(14), Theme.px(14)));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (final String[] preset : presets) {
            PresetCard card = new PresetCard(preset[0], preset[1], preset[2], preset[3]);
            card.setOnClick(new Runnable() {
                @Override
                public void run() {
                    state.selectPreset(GuiState.PRESET_NONE.equals(preset[0]) ? "" : preset[0]);
                    refreshPresets();
                    frame.log("INFO", "Preset applied: " + preset[1]);
                }
            });
            presetCards.add(card);
            grid.add(card);
        }
        grid.add(buildPresetHintTile());
        return grid;
    }

    private JPanel buildPresetHintTile() {
        Card card = new Card();
        JPanel body = Ui.column();
        JPanel titleRow = new JPanel();
        titleRow.setOpaque(false);
        titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
        JLabel icon = Ui.iconLabel("info", 16, () -> Theme.ACCENT2);
        titleRow.add(icon);
        titleRow.add(Box.createHorizontalStrut(Theme.px(8)));
        JLabel title = Ui.h3(I18n.t("ui.explicit.rules.win"));
        title.setFont(Theme.font(Font.BOLD, 14f));
        titleRow.add(title);
        titleRow.add(Box.createHorizontalGlue());
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(titleRow);
        body.add(Box.createVerticalStrut(Theme.px(8)));
        JLabel text = Ui.muted(I18n.t("ui.explicit.keep.rules.merge.after.the.preset"));
        text.setFont(Theme.font(Font.PLAIN, 12f));
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(text);
        card.body(body);
        return card;
    }

    private JPanel presetNote() {
        JPanel row = new JPanel(new BorderLayout(Theme.px(10), 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel note = Ui.faint(I18n.t("ui.presets.only.add.keep.rules.explicit.keep.rules.are.preserved"));
        note.setFont(Theme.font(Font.PLAIN, 12f));
        row.add(note, BorderLayout.WEST);
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        JLabel label = Ui.faint(I18n.t("ui.active.2"));
        label.setFont(Theme.font(Font.PLAIN, 12f));
        right.add(label);
        right.add(Box.createHorizontalStrut(Theme.px(6)));
        right.add(currentPreset);
        row.add(right, BorderLayout.EAST);
        return Ui.limitHeight(row);
    }

    void refreshPresets() {
        String active = state.presetName();
        for (PresetCard card : presetCards) {
            boolean selected = GuiState.PRESET_NONE.equals(card.value())
                    ? active.isEmpty() : card.value().equals(active);
            card.setPresetSelected(selected);
        }
        currentPreset.setText(active.isEmpty() ? I18n.t("ui.default.2")
                : active.toUpperCase(java.util.Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // keep rules and policy
    // ------------------------------------------------------------------

    private Card buildKeepCard() {
        Card card = new Card("rules", I18n.t("ui.keep.rules"),
                I18n.t("ui.exclude.types.and.members.from.every.transform"), () -> Theme.ACCENT);
        card.headerAction(ruleCount);
        ruleCount.setFont(Theme.font(Font.PLAIN, 12f));
        state.keep.setRows(12);
        JScrollPane scroll = editorScroll(state.keep, 12);
        card.fill(scroll);
        return card;
    }

    private JPanel buildPolicyColumn() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        Card policy = new Card("lock", I18n.t("ui.public.api"),
                I18n.t("ui.control.how.public.and.protected.members.are.treated"), () -> Theme.WARNING);
        JPanel policyBody = new JPanel();
        policyBody.setOpaque(false);
        policyBody.setLayout(new BoxLayout(policyBody, BoxLayout.Y_AXIS));
        policyBody.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.hard.keep.public.api"),
                I18n.t("ui.public.classes.keep.their.original.identity.entirely"), state.keepPublicApi)));
        policyBody.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.soft.keep.public.names"),
                I18n.t("ui.public.names.stay.bodies.are.still.transformed"), state.softKeepPublicApi)));

        policy.body(policyBody);
        policy.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(policy);
        column.add(Box.createVerticalStrut(Theme.px(14)));

        Card syntax = new Card("info", I18n.t("ui.rule.syntax"),
                I18n.t("ui.examples.that.cover.the.common.cases"), () -> Theme.ACCENT2);
        JPanel syntaxBody = new JPanel();
        syntaxBody.setOpaque(false);
        syntaxBody.setLayout(new BoxLayout(syntaxBody, BoxLayout.Y_AXIS));
        syntaxBody.add(syntaxRow("com.example.**", I18n.t("ui.glob.on.classes.and.nested.packages")));
        syntaxBody.add(syntaxRow("com.example.Main#main", I18n.t("ui.pin.one.member")));
        syntaxBody.add(syntaxRow("@com.google.**", I18n.t("ui.keep.annotated.members")));
        syntaxBody.add(syntaxRow("@io.zipit.annotations.Keep", I18n.t("ui.annotation.from.zipit.annotations")));
        syntaxBody.add(syntaxRow("**Controller", I18n.t("ui.suffix.glob.for.framework.types")));
        syntax.body(syntaxBody);
        syntax.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(syntax);
        return column;
    }

    private JComponent syntaxRow(String pattern, String description) {
        JPanel row = new JPanel(new BorderLayout(Theme.px(10), 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(Theme.px(3), 0, Theme.px(3), 0));
        JLabel code = Ui.colored(pattern, () -> Theme.ACCENT2, 12f, Font.PLAIN);
        code.setFont(Theme.mono(12f));
        code.setPreferredSize(new Dimension(Theme.px(172), Theme.px(18)));
        row.add(code, BorderLayout.WEST);
        JLabel desc = Ui.faint(description);
        desc.setFont(Theme.font(Font.PLAIN, 12f));
        row.add(desc, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(24)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private Card buildSoftKeepCard() {
        Card card = new Card("layers", I18n.t("ui.soft.keep.rules"),
                I18n.t("ui.keep.names.but.still.allow.body.transforms"), () -> Theme.INFO);
        state.softKeep.setRows(8);
        card.fill(editorScroll(state.softKeep, 8));
        return card;
    }

    private Card buildLibrariesCard() {
        Card card = new Card("box", I18n.t("ui.classpath.libraries"),
                I18n.t("ui.extra.jars.used.to.resolve.the.hierarchy.and.verify.output"), () -> Theme.ACCENT2);
        state.libraries.setRows(8);
        JPanel body = new JPanel(new BorderLayout(0, Theme.px(8)));
        body.setOpaque(false);
        body.add(editorScroll(state.libraries, 7), BorderLayout.CENTER);
        JLabel hint = Ui.faint(I18n.t("rules.classpathHint"));
        hint.setFont(Theme.font(Font.PLAIN, 12f));
        body.add(hint, BorderLayout.SOUTH);
        card.fill(body);
        return card;
    }

    private Card buildMappingNotesCard() {
        Card card = new Card("file", I18n.t("ui.release.mappings"),
                I18n.t("ui.stable.names.across.releases"), () -> Theme.SUCCESS);
        JPanel body = Ui.column();
        body.add(note(I18n.t("ui.mapping.file"),
                I18n.t("ui.written.next.to.the.output.zipit.mapping.txt.keep.it.with.each")));
        body.add(note(I18n.t("ui.previous.mapping"),
                I18n.t("ui.feed.the.last.mapping.back.in.to.keep.published.names.stable")));
        body.add(note(I18n.t("ui.audit.report"),
                I18n.t("ui.html.and.json.paths.are.configured.on.the.enterprise.page")));
        body.add(Box.createVerticalStrut(Theme.px(8)));
        JButton open = Ui.button(I18n.t("ui.open.build.settings"), "settings");
        open.setAlignmentX(Component.LEFT_ALIGNMENT);
        open.addActionListener(e -> frame.showPage(ZipitFrame.PAGE_TRANSFORMS));
        body.add(open);
        card.body(body);
        return card;
    }

    private JComponent note(String title, String text) {
        JPanel row = new JPanel(new BorderLayout(Theme.px(10), 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(Theme.px(2), 0, Theme.px(2), 0));
        JLabel heading = Ui.bold(title);
        heading.setFont(Theme.font(Font.BOLD, 12f));
        heading.setPreferredSize(new Dimension(Theme.px(130), Theme.px(18)));
        row.add(heading, BorderLayout.WEST);
        JLabel label = Ui.muted(text);
        label.setFont(Theme.font(Font.PLAIN, 12f));
        row.add(label, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(26)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void addSectionHeader(JPanel body, String index, String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout(Theme.px(12), 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(Theme.px(4), Theme.px(2),
                Theme.px(12), Theme.px(2)));
        JLabel number = Ui.colored(index, () -> Theme.ACCENT2, 12f, Font.BOLD);
        number.setPreferredSize(new Dimension(Theme.px(24), Theme.px(18)));
        header.add(number, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel heading = Ui.h2(title);
        heading.setFont(Theme.font(Font.BOLD, 17f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(heading);
        JLabel sub = Ui.muted(subtitle);
        sub.setFont(Theme.font(Font.PLAIN, 12f));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(Box.createVerticalStrut(Theme.px(2)));
        text.add(sub);
        header.add(text, BorderLayout.CENTER);
        body.add(Ui.limitHeight(header));
    }

    private JPanel twoColumns(JComponent left, JComponent right) {
        JPanel grid = new JPanel(new GridLayout(1, 2, Theme.px(16), Theme.px(16)));
        grid.setOpaque(false);
        grid.add(left);
        grid.add(right);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        return grid;
    }

    private JScrollPane editorScroll(JTextArea area, int rows) {
        area.setRows(rows);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        scroll.getViewport().setBackground(Theme.CARD);
        scroll.setPreferredSize(new Dimension(Theme.px(300), Theme.px(rows * 22)));
        return scroll;
    }

    private void updateRuleCount() {
        int count = GuiState.lines(state.keep.getText()).size();
        ruleCount.setText(I18n.t(count == 1 ? "rules.ruleCount" : "rules.ruleCountPlural", count));
        if (ruleCount.getParent() != null) {
            ruleCount.getParent().revalidate();
        }
    }
}
