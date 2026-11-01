package com.makeasaying.editor.map.validator;

import com.makeasaying.data.world.RoomLoader;
import com.makeasaying.data.world.RoomValidator;
import com.makeasaying.data.world.ValidationFinding;
import com.makeasaying.data.world.ValidationSeverity;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomLayerType;
import com.makeasaying.engine.world.Tileset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Presenter for in-editor validation panel per M6a brief.
 * Pure logic, headlessly testable.
 * - Debounces validation by frame interval (e.g. every 30 frames).
 * - Categorizes findings by severity.
 * - Extracts target cell/layer coordinates for click-to-focus navigation.
 */
public final class EditorValidatorPresenter {
    public static final int DEFAULT_DEBOUNCE_FRAMES = 30;

    private static final Pattern COORD_PATTERN = Pattern.compile("\\(([0-9]+),\\s*([0-9]+)\\)");
    private static final Pattern BRACKET_COORD_PATTERN = Pattern.compile("\\[([0-9]+),\\s*([0-9]+)");

    public record LocationTarget(int tileX, int tileY, RoomLayerType layer) {}

    private final RoomValidator validator;
    private final int debounceInterval;

    private long lastValidatedFrame = -1;
    private List<ValidationFinding> cachedFindings = Collections.emptyList();
    private final List<ValidationFinding> errors = new ArrayList<>();
    private final List<ValidationFinding> warnings = new ArrayList<>();
    private final List<ValidationFinding> infos = new ArrayList<>();

    public EditorValidatorPresenter(RoomValidator validator, int debounceInterval) {
        this.validator = validator != null ? validator : new RoomValidator();
        this.debounceInterval = debounceInterval > 0 ? debounceInterval : DEFAULT_DEBOUNCE_FRAMES;
    }

    public EditorValidatorPresenter() {
        this(new RoomValidator(), DEFAULT_DEBOUNCE_FRAMES);
    }

    public void update(long frameCount, RoomDocument doc, Map<String, Tileset> tilesets) {
        if (lastValidatedFrame != -1 && (frameCount - lastValidatedFrame) < debounceInterval) {
            return;
        }
        forceValidate(frameCount, doc, tilesets);
    }

    public void forceValidate(long frameCount, RoomDocument doc, Map<String, Tileset> tilesets) {
        if (doc == null) {
            cachedFindings = Collections.emptyList();
            errors.clear();
            warnings.clear();
            infos.clear();
            lastValidatedFrame = frameCount;
            return;
        }

        RoomLoader.RoomData data = RoomLoader.toData(doc.toRoom(), RoomLoader.CURRENT_VERSION);
        cachedFindings = validator.validate(data, tilesets);

        errors.clear();
        warnings.clear();
        infos.clear();

        for (ValidationFinding f : cachedFindings) {
            switch (f.severity()) {
                case ERROR -> errors.add(f);
                case WARNING -> warnings.add(f);
                case INFO -> infos.add(f);
            }
        }
        lastValidatedFrame = frameCount;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<ValidationFinding> getAllFindings() {
        return cachedFindings;
    }

    public List<ValidationFinding> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<ValidationFinding> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public List<ValidationFinding> getInfos() {
        return Collections.unmodifiableList(infos);
    }

    public LocationTarget parseLocationTarget(ValidationFinding finding) {
        if (finding == null || finding.location() == null || finding.location().isBlank()) {
            return null;
        }

        String loc = finding.location();
        RoomLayerType layer = null;
        for (RoomLayerType t : RoomLayerType.values()) {
            if (loc.contains(t.name())) {
                layer = t;
                break;
            }
        }

        Matcher matcher = COORD_PATTERN.matcher(loc);
        if (matcher.find()) {
            int x = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            if (loc.contains("spawn")) {
                // Spawn locations are in world pixel coordinates (16 px per tile)
                return new LocationTarget(x / 16, y / 16, layer);
            }
            return new LocationTarget(x, y, layer);
        }

        Matcher bracketMatcher = BRACKET_COORD_PATTERN.matcher(loc);
        if (bracketMatcher.find()) {
            int pxX = Integer.parseInt(bracketMatcher.group(1));
            int pxY = Integer.parseInt(bracketMatcher.group(2));
            return new LocationTarget(pxX / 16, pxY / 16, layer);
        }

        return null;
    }
}
