/**
* Copyright 2020 OPSLI 快速开发平台 https://www.opsli.com
* <p>
* Licensed under the Apache License, Version 2.0 (the "License"); you may not
* use this file except in compliance with the License. You may obtain a copy of
* the License at
* <p>
* http://www.apache.org/licenses/LICENSE-2.0
* <p>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations under
* the License.
*/
package org.opsli.modulars.system.org.web;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNodeConfig;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.opsli.api.base.result.ResultVo;
import org.opsli.api.web.system.org.SysOrgRestApi;
import org.opsli.api.wrapper.system.org.SysOrgModel;
import org.opsli.api.wrapper.system.user.UserModel;
import org.opsli.api.wrapper.system.user.UserOrgRefModel;
import org.opsli.common.annotation.ApiRestController;
import org.opsli.common.annotation.EnableLog;
import org.opsli.common.annotation.RequiresPermissionsCus;
import org.opsli.common.constants.MyBatisConstants;
import org.opsli.common.utils.FieldUtil;
import org.opsli.common.utils.ListDistinctUtil;
import org.opsli.common.utils.WrapperUtil;
import org.opsli.core.base.controller.BaseRestController;
import org.opsli.core.persistence.querybuilder.GenQueryBuilder;
import org.opsli.core.persistence.querybuilder.QueryBuilder;
import org.opsli.core.persistence.querybuilder.WebQueryBuilder;
import org.opsli.core.utils.OrgUtil;
import org.opsli.core.utils.TreeBuildUtil;
import org.opsli.core.utils.UserUtil;
import org.opsli.modulars.system.org.entity.SysOrg;
import org.opsli.modulars.system.org.service.ISysOrgService;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * 组织机构 Controller
 *
 * @author Parker
 * @date 2021-02-07 18:24:38
 */
@Api(tags = SysOrgRestApi.TITLE)
@Slf4j
@ApiRestController("/system/org")
public class SysOrgRestController extends BaseRestController<SysOrg, SysOrgModel, ISysOrgService>
    implements SysOrgRestApi {

    /** 虚拟总节点 ID */
    private static final String VIRTUAL_TOTAL_NODE = "-1";
    /** 父节点ID */
    private static final String PARENT_ID = "0";
    /** 排序字段 */
    private static final String SORT_FIELD = "sortNo";
    /** 分割符 */
    private static final String DELIMITER = ",";

    /**
     * 获得当前用户下 组织
     * @return ResultVo
     */
    @ApiOperation(value = "获得当前用户下 组织", notes = "获得当前用户下 组织")
    @Override
    public ResultVo<?> findTreeByDefWithUserToLike() {
        // 生成 全部/未分组
        String parentId = PARENT_ID;
        List<SysOrgModel> orgModelList = OrgUtil.createDefShowNodes(parentId, Lists.newArrayList());

        QueryBuilder<SysOrg> queryBuilder = new GenQueryBuilder<>();
        QueryWrapper<SysOrg> wrapper = queryBuilder.build();
        UserModel currUser = UserUtil.getUser();
        List<UserOrgRefModel> orgListByUserId = OrgUtil.getOrgListByUserId(currUser.getId());
        if(!CollUtil.isEmpty(orgListByUserId)){
            List<String> parentIdList = Lists.newArrayListWithCapacity(orgListByUserId.size());

            // 处理ParentId数据
            for (UserOrgRefModel userOrgRefModel : orgListByUserId) {
                String orgId = userOrgRefModel.getOrgId();
                String orgIds = userOrgRefModel.getOrgIds();
                // 减掉 结尾自身 orgId  得到  org表中 parentIds
                String parentIds =
                        StrUtil.replace(orgIds, StrUtil.prependIfMissing(orgId, DELIMITER), "");
                parentIdList.add(parentIds);
            }

            // 去重
            parentIdList = ListDistinctUtil.distinct(parentIdList);

            List<String> finalParentIdList = parentIdList;
            wrapper.and(wra -> {
                // 增加右模糊 查询条件
                for (int i = 0; i < finalParentIdList.size(); i++) {
                    // 右模糊匹配
                    wra.likeRight(
                            FieldUtil.humpToUnderline(MyBatisConstants.FIELD_PARENT_IDS),
                            finalParentIdList.get(i));

                    if(i < finalParentIdList.size() - 1){
                        wra.or();
                    }
                }
            });

            // 获得组织
            List<SysOrg> dataList = IService.findList(wrapper);
            if(CollUtil.isNotEmpty(dataList)){
                for (SysOrg sysOrg : dataList) {
                    // 如果父级ID 与 当前检索父级ID 一致 则默认初始化ID为主ID
                    if(!CollUtil.contains(parentIdList, sysOrg.getParentIds())){
                        continue;
                    }

                    sysOrg.setParentId(parentId);
                }
                orgModelList.addAll(
                        WrapperUtil.transformInstance(dataList, modelClazz)
                );
            }
        }

        // 处理组织树
        return handleOrgTree(parentId, orgModelList, false);
    }

    /**
     * 获得组织树 懒加载
     * @return ResultVo
     */
    @ApiOperation(value = "获得组织树 懒加载", notes = "获得组织树 懒加载")
    @Override
    public ResultVo<?> findTreeLazy(String parentId, String id) {
        List<SysOrgModel> orgModelList;
        if(StringUtils.isEmpty(parentId)){
            orgModelList = Lists.newArrayList();
            // 生成根节点组织
            SysOrgModel model = getGenOrgModel();
            parentId = model.getParentId();
            orgModelList.add(model);
        }else{
            QueryBuilder<SysOrg> queryBuilder = new GenQueryBuilder<>();
            QueryWrapper<SysOrg> wrapper = queryBuilder.build();
            wrapper.eq(FieldUtil.humpToUnderline(MyBatisConstants.FIELD_PARENT_ID), parentId);

            // 如果传入ID 则不包含自身
            if(StringUtils.isNotEmpty(id)){
                wrapper.notIn(
                        FieldUtil.humpToUnderline(MyBatisConstants.FIELD_ID), id);

            }

            // 获得组织
            List<SysOrg> dataList = IService.findList(wrapper);
            orgModelList = WrapperUtil.transformInstance(dataList, modelClazz);
        }

        // 处理组织树
        return handleOrgTree(parentId, orgModelList, true);
    }

    /**
     * 获得组织树
     * @return ResultVo
     */
    @ApiOperation(value = "获得组织树", notes = "获得组织树")
    @RequiresPermissions("system_org_select")
    @Override
    public ResultVo<?> findTreeByDef(boolean isGen, String id) {
        List<SysOrgModel> orgModelList = Lists.newArrayList();
        String parentId = PARENT_ID;
        if(isGen){
            // 生成根节点组织
            SysOrgModel model = getGenOrgModel();
            parentId = model.getParentId();
            orgModelList.add(model);
        }

        QueryBuilder<SysOrg> queryBuilder = new GenQueryBuilder<>();
        QueryWrapper<SysOrg> wrapper = queryBuilder.build();
//        // 左模糊匹配
//        wrapper.likeLeft(
//                FieldUtil.humpToUnderline(MyBatisConstants.FIELD_PARENT_IDS), parentId);

        // 如果传入ID 则不包含自身
        if(StringUtils.isNotEmpty(id)){
            wrapper.notIn(
                    FieldUtil.humpToUnderline(MyBatisConstants.FIELD_ID), id);

        }

        // 获得组织
        List<SysOrg> dataList = IService.findList(wrapper);
        if(CollUtil.isNotEmpty(dataList)){
            orgModelList.addAll(
                    WrapperUtil.transformInstance(dataList, modelClazz)
            );
        }

        // 处理组织树
        return handleOrgTree(parentId, orgModelList, false);
    }




    // ==============

    /**
    * 组织机构 查一条
    * @param model 模型
    * @return ResultVo
    */
    @ApiOperation(value = "获得单条组织机构", notes = "获得单条组织机构 - ID")
    @RequiresPermissions("system_org_select")
    @Override
    public ResultVo<SysOrgModel> get(SysOrgModel model) {
        if(model != null){
            if(StringUtils.equals(PARENT_ID, model.getId())){
                // 生成根节点组织
                model = getGenOrgModel();
            }else{
                // 如果系统内部调用 则直接查数据库
                if (model.getIzApi() != null && model.getIzApi()){
                    model = IService.get(model);
                }
            }
        }

        return ResultVo.success(model);
    }

    /**
    * 组织机构 新增
    * @param model 模型
    * @return ResultVo
    */
    @ApiOperation(value = "新增组织机构数据", notes = "新增组织机构数据")
    @RequiresPermissions("system_org_insert")
    @EnableLog
    @Override
    public ResultVo<?> insert(SysOrgModel model) {
        // 演示模式 不允许操作
        //super.demoError();

        // 调用新增方法
        IService.insert(model);
        return ResultVo.success("新增组织机构成功");
    }

    /**
    * 组织机构 修改
    * @param model 模型
    * @return ResultVo
    */
    @ApiOperation(value = "修改组织机构数据", notes = "修改组织机构数据")
    @RequiresPermissions("system_org_update")
    @EnableLog
    @Override
    public ResultVo<?> update(SysOrgModel model) {
        // 演示模式 不允许操作
        super.demoError();

        // 调用修改方法
        IService.update(model);
        return ResultVo.success("修改组织机构成功");
    }


    /**
    * 组织机构 删除
    * @param id ID
    * @return ResultVo
    */
    @ApiOperation(value = "删除组织机构数据", notes = "删除组织机构数据")
    @RequiresPermissions("system_org_update")
    @EnableLog
    @Override
    public ResultVo<?> del(String id){
        // 演示模式 不允许操作
        super.demoError();

        IService.delete(id);
        return ResultVo.success("删除组织机构成功");
    }

    /**
    * 组织机构 批量删除
    * @param ids ID 数组
    * @return ResultVo
    */
    @ApiOperation(value = "批量删除组织机构数据", notes = "批量删除组织机构数据")
    @RequiresPermissions("system_org_update")
    @EnableLog
    @Override
    public ResultVo<?> delAll(String ids){
        // 演示模式 不允许操作
        super.demoError();

        String[] idArray = Convert.toStrArray(ids);
        IService.deleteAll(idArray);

        return ResultVo.success("批量删除组织机构成功");
    }


    /**
    * 组织机构 Excel 导出
    * @param request request
    * @param response response
    */
    @ApiOperation(value = "导出Excel", notes = "导出Excel")
    @RequiresPermissionsCus("system_org_export")
    @EnableLog
    @Override
    public void exportExcel(HttpServletRequest request, HttpServletResponse response) {
        // 当前方法
        Method method = ReflectUtil.getMethodByName(this.getClass(), "exportExcel");
        QueryBuilder<SysOrg> queryBuilder = new WebQueryBuilder<>(entityClazz, request.getParameterMap());
        super.excelExport(SysOrgRestApi.SUB_TITLE, queryBuilder.build(), response, method);
    }

    /**
    * 组织机构 Excel 导入
    * @param request 文件流 request
    * @return ResultVo
    */
    @ApiOperation(value = "导入Excel", notes = "导入Excel")
    @RequiresPermissions("system_org_import")
    @EnableLog
    @Override
    public ResultVo<?> importExcel(MultipartHttpServletRequest request) {
        return super.importExcel(request);
    }

    /**
    * 组织机构 Excel 下载导入模版
    * @param response response
    */
    @ApiOperation(value = "导出Excel模版", notes = "导出Excel模版")
    @RequiresPermissionsCus("system_org_import")
    @Override
    public void importTemplate(HttpServletResponse response) {
        // 当前方法
        Method method = ReflectUtil.getMethodByName(this.getClass(), "importTemplate");
        super.importTemplate(SysOrgRestApi.SUB_TITLE, response, method);
    }

    // ==============================

    /**
     * 生成根节点ID
     * @return SysOrgModel
     */
    private SysOrgModel getGenOrgModel() {
        SysOrgModel model = new SysOrgModel();
        model.setId(PARENT_ID);
        model.setOrgName("组织架构");
        model.setSortNo(-1);
        model.setParentId(VIRTUAL_TOTAL_NODE);
        return model;
    }



    /**
     * 处理组织树
     * @param parentId 父级ID
     * @param orgModelList 组织集合
     * @param izLazy 是否懒加载
     * @return ResultVo
     */
    private ResultVo<?> handleOrgTree(String parentId, List<SysOrgModel> orgModelList, boolean izLazy) {
        //配置
        TreeNodeConfig treeNodeConfig = new TreeNodeConfig();
        // 自定义属性名 都要默认值的
        treeNodeConfig.setWeightKey(SORT_FIELD);
        // 最大递归深度 最多支持4层
        treeNodeConfig.setDeep(4);

        //转换器
        List<Tree<Object>> treeNodes = TreeBuildUtil.INSTANCE.build(orgModelList, parentId, treeNodeConfig);

        // 是否懒加载
        if(izLazy){
            // 处理是否包含子集
            super.handleTreeHasChildren(treeNodes,
                    (parentIds)-> IService.hasChildren(parentIds));
        }else{
            Set<String> parentIdSet = Sets.newHashSet();
            for (SysOrgModel sysOrgModel : orgModelList) {
                parentIdSet.add(sysOrgModel.getParentId());
            }

            // 处理是否包含子集
            super.handleTreeHasChildren(treeNodes,
                    (parentIds)-> IService.hasChildren(parentIdSet));
        }

        return ResultVo.success(treeNodes);
    }

}
