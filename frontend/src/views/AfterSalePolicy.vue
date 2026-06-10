<template>
  <div class="policyPage">
    <button class="back" @click="$router.back()">← 返回</button>
    <h1>平台售后说明</h1>
    <p class="updateDate">更新日期：2026年6月</p>

    <section>
      <h2>一、基础原则</h2>
      <p>Second hand 是一个C2C二手交易信息撮合平台，平台本身不持有商品、不介入物流、不对商品质量兜底。买卖双方通过平台达成交易，交易风险由双方自行评估和承担。</p>
      <ul>
        <li><strong>优先级：</strong>商品详情页约定 + 站内聊天约定 > 平台通用规则。仅平台站内聊天记录有效，微信/QQ/线下口头约定平台不予采信。</li>
        <li><strong>平台角色：</strong>平台仅提供信息展示、交易撮合、纠纷仲裁服务，不对商品真伪、质量、物流时效做任何保证。</li>
      </ul>
    </section>

    <section>
      <h2>二、责任判定</h2>
      <table>
        <thead><tr><th>场景</th><th>责任方</th><th>处理方式</th></tr></thead>
        <tbody>
          <tr><td>货不对板、隐瞒暗病、假货</td><td>卖家</td><td>卖家承担往返运费，支持退货/仅退款</td></tr>
          <tr><td>发错货、发货破损</td><td>卖家</td><td>卖家承担往返运费，支持退货/仅退款</td></tr>
          <tr><td>人为损坏、主观不想要</td><td>买家</td><td>无约定则驳回售后；退货买家自理运费</td></tr>
          <tr><td>物流破损（签收前拒收）</td><td>卖家</td><td>卖家全责，支持全额退款</td></tr>
          <tr><td>物流破损（签收后发现）</td><td>谁举证谁追责</td><td>需提供签收时开箱证据</td></tr>
        </tbody>
      </table>
    </section>

    <section>
      <h2>三、售后时效红线</h2>
      <ul>
        <li><strong>未确认收货：</strong>全周期可发起售后，无时间限制。</li>
        <li><strong>确认收货后：</strong>7个自然天内为售后窗口期。超7天系统自动关闭售后入口，平台不再受理任何维权申请，订单资金彻底清算完结。</li>
      </ul>
    </section>

    <section>
      <h2>四、售后类型及流程</h2>

      <h3>4.1 仅退款（未发货/未签收，无需退货）</h3>
      <ol>
        <li>买家提交仅退款申请</li>
        <li>卖家72小时（3自然天）内处理：
          <ul>
            <li><strong>同意：</strong>原路全额退款，售后闭环，订单关闭。</li>
            <li><strong>拒绝：</strong>买家3天内可选"平台介入"；超3天不操作则售后关闭，订单继续履约。</li>
            <li><strong>超时未处理：</strong>系统自动全额退款，售后完结。</li>
          </ul>
        </li>
      </ol>

      <h3>4.2 仅退款（已收到货、不退回商品）</h3>
      <p>平台不会自动同意此类仅退款。仅在以下场景生效：</p>
      <ul>
        <li>买卖双方协商一致，卖家手动同意退款；</li>
        <li>平台仲裁判定假货/全损无法退回，裁定仅退款。</li>
      </ul>
      <p>卖家超时不处理<strong>不会</strong>自动退款。买家申请→卖家72h审核→同意则退款；拒绝则买家可申请平台介入。</p>

      <h3>4.3 退货退款（核心链路，四段超时）</h3>
      <div class="flowBox">
        <div class="flowStep"><strong>阶段1：</strong>买家提交退货退款 → 卖家72h审核<br/>
          同意→进入待寄件；拒绝→买家3天介入，超时关闭<br/>
          卖家72h未处理→系统自动同意退货，进入待寄件</div>
        <div class="flowStep"><strong>阶段2：</strong>待买家寄件 → 买家7自然天内填写退货单号<br/>
          7天内填单号→进入待签收；超7天未填→售后关闭，订单正常打款</div>
        <div class="flowStep"><strong>阶段3：</strong>已填退货单号 → 卖家10自然天内确认收货<br/>
          卖家确认无误→退款原路返还；卖家拒收→买家可申请平台介入<br/>
          超10天未确认→系统默认签收无误，自动退款</div>
        <div class="flowStep"><strong>阶段4：</strong>退款完成，售后闭环</div>
      </div>

      <h3>4.4 部分退款（商品自留、折价补偿）</h3>
      <p>无强制超时机制。双方协商金额，卖家同意即部分退款完结；卖家拒绝则买家可走平台仲裁裁定折价金额。</p>
    </section>

    <section>
      <h2>五、平台介入（仲裁）</h2>
      <ol>
        <li><strong>触发条件：</strong>卖家拒绝售后 → 买家点击"申请平台介入"。</li>
        <li><strong>举证规则——谁主张谁举证：</strong>
          <ul>
            <li>买家主张货不对板：需提供完整带时间戳开箱视频（含快递面单+未拆封外包装+实物）、商品详情截图、站内聊天记录。缺核心开箱视频大概率败诉。</li>
            <li>卖家主张买家人为损坏：需提供发货打包全流程视频、收货拆包验货视频。无法举证则判卖家败诉。</li>
          </ul>
        </li>
        <li><strong>仲裁三结果：</strong>
          <ul>
            <li>全额退款（退货退款或仅退款）</li>
            <li>部分折价退款</li>
            <li>驳回售后申请</li>
          </ul>
          裁决为平台最终结果，不可二次申诉。
        </li>
        <li><strong>审核时效：</strong>平台3~5个工作日出具裁决结果。</li>
      </ol>
    </section>

    <section>
      <h2>六、免责声明</h2>
      <p>Second hand 作为信息撮合平台，对于以下情形不承担责任：</p>
      <ul>
        <li>商品真伪、质量、功能完好性等实质问题；</li>
        <li>买卖双方通过非平台渠道（微信、QQ、线下等）进行的沟通和约定；</li>
        <li>物流运输过程中产生的延误、损毁、丢失（由物流公司承担责任）；</li>
        <li>因不可抗力导致的交易无法履行。</li>
      </ul>
      <p>平台鼓励买卖双方在交易前充分沟通，保留站内聊天记录作为后续维权依据。</p>
    </section>
  </div>
</template>

<style scoped>
.policyPage {
  max-width: 760px; margin: 0 auto; padding: 16px 0 60px;
  font-size: 14px; line-height: 1.75; color: rgba(0,0,0,0.8);
}
h1 { font-size: 22px; margin: 12px 0 4px; }
.updateDate { color: rgba(0,0,0,0.35); font-size: 12px; margin: 0 0 24px; }
h2 { font-size: 17px; margin: 28px 0 10px; padding-bottom: 6px; border-bottom: 1px solid rgba(0,0,0,0.08); }
h3 { font-size: 15px; margin: 16px 0 8px; }
ul, ol { padding-left: 22px; }
li { margin-bottom: 4px; }
section { margin-bottom: 16px; }
table { width: 100%; border-collapse: collapse; margin: 10px 0; font-size: 13px; }
th, td { border: 1px solid rgba(0,0,0,0.1); padding: 8px 10px; text-align: left; }
th { background: rgba(0,0,0,0.03); font-weight: 600; }
.flowBox { display: grid; gap: 10px; margin: 10px 0; }
.flowStep {
  padding: 10px 14px; border: 1px solid rgba(0,0,0,0.08); border-radius: 10px;
  background: rgba(0,0,0,0.01); font-size: 13px;
}
.back { border: 1px solid rgba(0,0,0,0.12); background: white; padding: 6px 10px; border-radius: 10px; cursor: pointer; margin-bottom: 10px; }
</style>
