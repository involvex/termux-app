package com.invapp.app.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorkflowBarHelperTest {

    @Test
    public void defaultVisible_omitsRunAndStop() {
        List<String> def = Arrays.asList(WorkflowBarHelper.DEFAULT_VISIBLE_IDS);
        assertTrue(def.contains(WorkflowBarHelper.ID_PULL));
        assertTrue(def.contains(WorkflowBarHelper.ID_AI));
        assertFalse(def.contains(WorkflowBarHelper.ID_RUN));
        assertFalse(def.contains(WorkflowBarHelper.ID_AI_STOP));
        assertFalse(def.contains(WorkflowBarHelper.ID_MORE));
    }

    @Test
    public void filterToCatalogOrder_keepsCatalogSequence() {
        boolean[] checked = new boolean[WorkflowBarHelper.ALL_ACTION_IDS.length];
        checked[8] = true; // ai_stop
        checked[0] = true; // pull
        checked[4] = true; // run
        List<String> out = WorkflowBarHelper.filterToCatalogOrder(checked);
        assertEquals(Arrays.asList(
            WorkflowBarHelper.ID_PULL,
            WorkflowBarHelper.ID_RUN,
            WorkflowBarHelper.ID_AI_STOP
        ), out);
    }

    @Test
    public void checkedFlags_roundTrip() {
        List<String> visible = Arrays.asList(
            WorkflowBarHelper.ID_CLONE, WorkflowBarHelper.ID_AI);
        boolean[] flags = WorkflowBarHelper.checkedFlags(visible);
        assertEquals(visible, WorkflowBarHelper.filterToCatalogOrder(flags));
    }
}
