package com.profetai.industrymap.controller;

import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.payloads.item.ComponentNode;
import com.profetai.industrymap.service.item.ItemCompositionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ItemCompositionService itemCompositionService;

    @Test
    @DisplayName("展開組成樹應回傳指定深度的節點")
    void getComponents_withDepth_shouldReturnTree() throws Exception {
        ComponentNode child = ComponentNode.builder()
                .itemId(2L).displayName("變速器").necessity(Necessity.COMMON).children(List.of()).build();
        ComponentNode root = ComponentNode.builder()
                .itemId(1L).displayName("腳踏車").endProduct(true).children(List.of(child)).build();
        when(itemCompositionService.expandTree(1L, 2, null, false)).thenReturn(root);

        mockMvc.perform(get("/api/products/1/components").param("depth", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("腳踏車"))
                .andExpect(jsonPath("$.data.children[0].displayName").value("變速器"));
    }

    @Test
    @DisplayName("展開不存在的產品應回傳 404")
    void getComponents_unknownProduct_shouldReturnNotFound() throws Exception {
        when(itemCompositionService.expandTree(99L, 1, null, false))
                .thenThrow(new ServerException("查無此品類節點", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/products/99/components"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("展開層數超出上限時應回傳 400")
    void getComponents_depthAboveLimit_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/products/1/components").param("depth", "99"))
                .andExpect(status().isBadRequest());
    }
}
