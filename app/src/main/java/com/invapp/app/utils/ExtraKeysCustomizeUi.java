package com.invapp.app.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.invapp.R;
import com.invapp.shared.errors.Error;
import com.invapp.shared.file.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Visual page-2 extra-keys editor: checkboxes + drag reorder, presets, import/export.
 * Writes via {@link ExtraKeysBarHelper}; does not touch ExtraKeysView touch handling.
 */
public final class ExtraKeysCustomizeUi {

    public interface ReloadCallback {
        /** Called on the UI thread after a successful properties write. */
        void onExtraKeysWritten();
    }

    private ExtraKeysCustomizeUi() {}

    public static void show(@NonNull Context context, @Nullable ReloadCallback reload) {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_extra_keys_editor, null);
        RecyclerView list = root.findViewById(R.id.extra_keys_editor_list);
        Button presetsBtn = root.findViewById(R.id.extra_keys_btn_presets);
        Button exportBtn = root.findViewById(R.id.extra_keys_btn_export);
        Button importBtn = root.findViewById(R.id.extra_keys_btn_import);

        final List<Row> rows = buildRows(context);
        final EditorAdapter adapter = new EditorAdapter(rows);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from < 0 || to < 0 || from == to) {
                    return false;
                }
                Row moved = rows.remove(from);
                rows.add(to, moved);
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // no-op
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }
        });
        touchHelper.attachToRecyclerView(list);
        adapter.setDragStarter(touchHelper);

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(R.string.title_customize_extra_keys)
            .setView(root)
            .setPositiveButton(android.R.string.ok, (d, which) -> {
                List<String> ids = selectedIdsInOrder(rows);
                ExtraKeysBarHelper.setPage2Ids(context, ids);
                applyWrite(context, ids, reload);
            })
            .setNeutralButton(R.string.action_workflow_reset_bar, (d, which) -> {
                ExtraKeysBarHelper.resetToDefault(context);
                List<String> ids = ExtraKeysBarHelper.getPage2Ids(context);
                applyWrite(context, ids, reload);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        presetsBtn.setOnClickListener(v -> showPresets(context, rows, adapter));
        exportBtn.setOnClickListener(v -> exportKeys(context, selectedIdsInOrder(rows)));
        importBtn.setOnClickListener(v -> importKeys(context, rows, adapter));

        dialog.show();
    }

    @NonNull
    private static List<Row> buildRows(@NonNull Context context) {
        List<String> current = ExtraKeysBarHelper.getPage2Ids(context);
        List<Row> rows = new ArrayList<>();
        for (String id : current) {
            int idx = indexOfId(id);
            if (idx >= 0) {
                rows.add(new Row(id, ExtraKeysBarHelper.PAGE2_LABELS[idx], true));
            }
        }
        for (int i = 0; i < ExtraKeysBarHelper.ALL_PAGE2_IDS.length; i++) {
            String id = ExtraKeysBarHelper.ALL_PAGE2_IDS[i];
            if (!containsId(rows, id)) {
                rows.add(new Row(id, ExtraKeysBarHelper.PAGE2_LABELS[i], false));
            }
        }
        return rows;
    }

    private static int indexOfId(@NonNull String id) {
        for (int i = 0; i < ExtraKeysBarHelper.ALL_PAGE2_IDS.length; i++) {
            if (ExtraKeysBarHelper.ALL_PAGE2_IDS[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsId(@NonNull List<Row> rows, @NonNull String id) {
        for (Row row : rows) {
            if (row.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static List<String> selectedIdsInOrder(@NonNull List<Row> rows) {
        List<String> ids = new ArrayList<>();
        for (Row row : rows) {
            if (row.checked) {
                ids.add(row.id);
            }
        }
        return ids;
    }

    private static void applyRowsFromIds(@NonNull List<Row> rows, @NonNull List<String> ids,
                                         @NonNull EditorAdapter adapter) {
        rows.clear();
        for (String id : ids) {
            int idx = indexOfId(id);
            if (idx >= 0) {
                rows.add(new Row(id, ExtraKeysBarHelper.PAGE2_LABELS[idx], true));
            }
        }
        for (int i = 0; i < ExtraKeysBarHelper.ALL_PAGE2_IDS.length; i++) {
            String id = ExtraKeysBarHelper.ALL_PAGE2_IDS[i];
            if (!containsId(rows, id)) {
                rows.add(new Row(id, ExtraKeysBarHelper.PAGE2_LABELS[i], false));
            }
        }
        adapter.notifyDataSetChanged();
    }

    private static void showPresets(@NonNull Context context, @NonNull List<Row> rows,
                                    @NonNull EditorAdapter adapter) {
        String[] labels = {
            context.getString(R.string.preset_extra_keys_minimal),
            context.getString(R.string.preset_extra_keys_bun_dev),
            context.getString(R.string.preset_extra_keys_ai)
        };
        new AlertDialog.Builder(context)
            .setTitle(R.string.action_extra_keys_presets)
            .setItems(labels, (d, which) -> {
                List<String> ids;
                switch (which) {
                    case 0:
                        ids = Arrays.asList(ExtraKeysBarHelper.PRESET_MINIMAL);
                        break;
                    case 1:
                        ids = Arrays.asList(ExtraKeysBarHelper.PRESET_BUN_DEV);
                        break;
                    case 2:
                    default:
                        ids = Arrays.asList(ExtraKeysBarHelper.PRESET_AI);
                        break;
                }
                applyRowsFromIds(rows, ids, adapter);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private static void exportKeys(@NonNull Context context, @NonNull List<String> ids) {
        if (!BackupHelper.isSharedStorageReady()) {
            Toast.makeText(context, R.string.error_extra_keys_storage, Toast.LENGTH_LONG).show();
            return;
        }
        File dir = ExtraKeysBarHelper.keysDirectory();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, ExtraKeysBarHelper.exportFileName());
        String json = ExtraKeysBarHelper.page2IdsToJson(ids);
        Error err = FileUtils.writeTextToFile("extra-keys export", out.getAbsolutePath(),
            StandardCharsets.UTF_8, json + "\n", false);
        if (err != null) {
            Toast.makeText(context, R.string.error_extra_keys_export, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(context,
            context.getString(R.string.msg_extra_keys_exported, out.getAbsolutePath()),
            Toast.LENGTH_LONG).show();
    }

    private static void importKeys(@NonNull Context context, @NonNull List<Row> rows,
                                   @NonNull EditorAdapter adapter) {
        if (!BackupHelper.isSharedStorageReady()) {
            Toast.makeText(context, R.string.error_extra_keys_storage, Toast.LENGTH_LONG).show();
            return;
        }
        File in = new File(ExtraKeysBarHelper.keysDirectory(), ExtraKeysBarHelper.exportFileName());
        if (!in.isFile()) {
            Toast.makeText(context, R.string.error_extra_keys_import_missing, Toast.LENGTH_LONG)
                .show();
            return;
        }
        StringBuilder contents = new StringBuilder();
        Error err = FileUtils.readTextFromFile("extra-keys import", in.getAbsolutePath(),
            StandardCharsets.UTF_8, contents, false);
        if (err != null) {
            Toast.makeText(context, R.string.error_extra_keys_import, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> ids = ExtraKeysBarHelper.page2IdsFromJson(contents.toString());
        if (ids == null) {
            Toast.makeText(context, R.string.error_extra_keys_import, Toast.LENGTH_SHORT).show();
            return;
        }
        String built = ExtraKeysBarHelper.buildExtraKeysJson(ids);
        if (!ExtraKeysBarHelper.isValidExtraKeysJson(built)) {
            Toast.makeText(context, R.string.error_extra_keys_import, Toast.LENGTH_SHORT).show();
            return;
        }
        applyRowsFromIds(rows, ids, adapter);
        Toast.makeText(context, R.string.msg_extra_keys_imported, Toast.LENGTH_SHORT).show();
    }

    private static void applyWrite(@NonNull Context context,
                                   @NonNull List<String> ids,
                                   @Nullable ReloadCallback reload) {
        String json = ExtraKeysBarHelper.buildExtraKeysJson(ids);
        if (!ExtraKeysBarHelper.isValidExtraKeysJson(json)) {
            Toast.makeText(context, R.string.error_extra_keys_write_failed, Toast.LENGTH_SHORT)
                .show();
            return;
        }
        if (ExtraKeysBarHelper.writeExtraKeysProperty(ids)) {
            if (reload != null) {
                reload.onExtraKeysWritten();
            }
            Toast.makeText(context, R.string.msg_extra_keys_updated, Toast.LENGTH_LONG)
                .show();
        } else {
            Toast.makeText(context, R.string.error_extra_keys_write_failed, Toast.LENGTH_SHORT)
                .show();
        }
    }

    private static final class Row {
        @NonNull final String id;
        @NonNull final String label;
        boolean checked;

        Row(@NonNull String id, @NonNull String label, boolean checked) {
            this.id = id;
            this.label = label;
            this.checked = checked;
        }
    }

    private static final class EditorAdapter extends RecyclerView.Adapter<EditorAdapter.VH> {
        private final List<Row> rows;
        private ItemTouchHelper dragHelper;

        EditorAdapter(@NonNull List<Row> rows) {
            this.rows = rows;
        }

        void setDragStarter(@NonNull ItemTouchHelper helper) {
            this.dragHelper = helper;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_extra_keys_editor_row, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Row row = rows.get(position);
            holder.check.setOnCheckedChangeListener(null);
            holder.check.setText(row.label);
            holder.check.setChecked(row.checked);
            holder.check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                row.checked = isChecked;
            });
            holder.handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && dragHelper != null) {
                    dragHelper.startDrag(holder);
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static final class VH extends RecyclerView.ViewHolder {
            final CheckBox check;
            final View handle;

            VH(@NonNull View itemView) {
                super(itemView);
                check = itemView.findViewById(R.id.extra_keys_row_check);
                handle = itemView.findViewById(R.id.extra_keys_row_handle);
            }
        }
    }
}
