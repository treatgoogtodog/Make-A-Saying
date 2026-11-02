package com.makeasaying.editor.map.commands;

import com.makeasaying.editor.core.command.EditorCommand;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.editor.map.model.InspectorModel;
import com.makeasaying.engine.world.RoomEntityData;

import java.util.Map;

/**
 * Commands for entity placement, moving, deleting, field editing, and platform gizmo dragging.
 * Undo and redo restore exact document state and sequence counter (DEC-028).
 */
public final class EntityCommands {
    private EntityCommands() {}

    public static final class PlaceEntityCommand implements EditorCommand {
        private final RoomDocument document;
        private final RoomEntityData entity;
        private final int priorSeq;

        public PlaceEntityCommand(RoomDocument document, RoomEntityData entity, int priorSeq) {
            this.document = document;
            this.entity = entity;
            this.priorSeq = priorSeq;
        }

        @Override
        public String getName() {
            return "Place Entity " + entity.instanceId();
        }

        @Override
        public void apply() {
            document.addEntity(entity);
        }

        @Override
        public void revert() {
            document.removeEntity(entity.instanceId());
            document.restoreEntitySeq(priorSeq);
        }
    }

    public static final class MoveEntityCommand implements EditorCommand {
        private final RoomDocument document;
        private final String instanceId;
        private final int oldX;
        private final int oldY;
        private final int newX;
        private final int newY;

        public MoveEntityCommand(RoomDocument document, String instanceId, int oldX, int oldY, int newX, int newY) {
            this.document = document;
            this.instanceId = instanceId;
            this.oldX = oldX;
            this.oldY = oldY;
            this.newX = newX;
            this.newY = newY;
        }

        @Override
        public String getName() {
            return "Move Entity " + instanceId;
        }

        @Override
        public void apply() {
            RoomEntityData current = document.getEntity(instanceId);
            if (current != null) {
                document.setEntity(instanceId, new RoomEntityData(
                    current.instanceId(),
                    current.definition(),
                    newX,
                    newY,
                    current.overrides()
                ));
            }
        }

        @Override
        public void revert() {
            RoomEntityData current = document.getEntity(instanceId);
            if (current != null) {
                document.setEntity(instanceId, new RoomEntityData(
                    current.instanceId(),
                    current.definition(),
                    oldX,
                    oldY,
                    current.overrides()
                ));
            }
        }
    }

    public static final class DeleteEntityCommand implements EditorCommand {
        private final RoomDocument document;
        private final RoomEntityData removedEntity;

        public DeleteEntityCommand(RoomDocument document, RoomEntityData removedEntity) {
            this.document = document;
            this.removedEntity = removedEntity;
        }

        @Override
        public String getName() {
            return "Delete Entity " + removedEntity.instanceId();
        }

        @Override
        public void apply() {
            document.removeEntity(removedEntity.instanceId());
        }

        @Override
        public void revert() {
            document.addEntity(removedEntity);
        }
    }

    public static final class EditOverrideFieldCommand implements EditorCommand {
        private final RoomDocument document;
        private final String instanceId;
        private final String componentId;
        private final String fieldName;
        private final Object oldValue;
        private final Object newValue;

        public EditOverrideFieldCommand(
            RoomDocument document,
            String instanceId,
            String componentId,
            String fieldName,
            Object oldValue,
            Object newValue
        ) {
            this.document = document;
            this.instanceId = instanceId;
            this.componentId = componentId;
            this.fieldName = fieldName;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        @Override
        public String getName() {
            return "Edit " + instanceId + " " + componentId + "." + fieldName;
        }

        @Override
        public void apply() {
            applyValue(newValue);
        }

        @Override
        public void revert() {
            applyValue(oldValue);
        }

        private void applyValue(Object val) {
            RoomEntityData current = document.getEntity(instanceId);
            if (current == null) return;

            Map<String, Map<String, Object>> newOverrides;
            if (val == null) {
                newOverrides = InspectorModel.removeOverride(current.overrides(), componentId, fieldName);
            } else {
                newOverrides = InspectorModel.setOverride(current.overrides(), componentId, fieldName, val);
            }

            document.setEntity(instanceId, new RoomEntityData(
                current.instanceId(),
                current.definition(),
                current.x(),
                current.y(),
                newOverrides
            ));
        }
    }

    public static final class MovePlatformEndCommand implements EditorCommand {
        private final RoomDocument document;
        private final String instanceId;
        private final int oldTravelX;
        private final int oldTravelY;
        private final int newTravelX;
        private final int newTravelY;

        public MovePlatformEndCommand(
            RoomDocument document,
            String instanceId,
            int oldTravelX,
            int oldTravelY,
            int newTravelX,
            int newTravelY
        ) {
            this.document = document;
            this.instanceId = instanceId;
            this.oldTravelX = oldTravelX;
            this.oldTravelY = oldTravelY;
            this.newTravelX = newTravelX;
            this.newTravelY = newTravelY;
        }

        @Override
        public String getName() {
            return "Move Platform Endpoint " + instanceId;
        }

        @Override
        public void apply() {
            setTravel(newTravelX, newTravelY);
        }

        @Override
        public void revert() {
            setTravel(oldTravelX, oldTravelY);
        }

        private void setTravel(int tx, int ty) {
            RoomEntityData current = document.getEntity(instanceId);
            if (current == null) return;

            Map<String, Map<String, Object>> ovr = InspectorModel.setOverride(current.overrides(), "platform", "travelX", tx);
            ovr = InspectorModel.setOverride(ovr, "platform", "travelY", ty);

            document.setEntity(instanceId, new RoomEntityData(
                current.instanceId(),
                current.definition(),
                current.x(),
                current.y(),
                ovr
            ));
        }
    }
}
